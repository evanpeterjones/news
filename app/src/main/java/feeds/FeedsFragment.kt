package feeds

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentFeedsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import common.*
import entries.EntriesFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.koin.android.viewmodel.ext.android.viewModel
import java.net.URI

class FeedsFragment : AppFragment(lockDrawer = false) {

    private val model: FeedsViewModel by viewModel()

    private var _binding: FragmentFeedsBinding? = null
    private val binding get() = _binding!!

    private val adapter =
        FeedsAdapter(scope = lifecycleScope, callback = object : FeedsAdapterCallback {
            override fun onSettingsClick(feed: FeedsAdapterItem) {
                findNavController().navigate(
                    FeedsFragmentDirections.actionFeedsFragmentToFeedSettingsFragment(
                        feedId = feed.id,
                    )
                )
            }

            override fun onFeedClick(feed: FeedsAdapterItem) {
                findNavController().navigate(
                    FeedsFragmentDirections.actionFeedsFragmentToFeedEntriesFragment(
                        EntriesFilter.OnlyFromFeed(feedId = feed.id)
                    )
                )
            }

            override fun onOpenHtmlLinkClick(feed: FeedsAdapterItem) {
                openLink(feed.alternateLink)
            }

            override fun openLinkClick(feed: FeedsAdapterItem) {
                openLink(feed.selfLink)
            }

            override fun onRenameClick(feed: FeedsAdapterItem) {
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.rename))
                    .setView(R.layout.dialog_rename_feed)
                    .setPositiveButton(R.string.rename) { dialogInterface, _ ->
                        val dialog = dialogInterface as AlertDialog
                        val title = dialog.findViewById<TextInputEditText>(R.id.title)!!

                        lifecycleScope.launchWhenResumed {
                            model.rename(feed.id, title.text.toString())
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { hideKeyboard() }
                    .show()

                val title = dialog.findViewById<TextInputEditText>(R.id.title)!!
                title.append(feed.title)
                title.requestFocus()

                requireContext().showKeyboard()
            }

            override fun onDeleteClick(feed: FeedsAdapterItem) {
                lifecycleScope.launchWhenResumed { model.delete(feed.id) }
            }
        })

    @Suppress("BlockingMethodInNonBlockingContext")
    private val importFeedsLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        lifecycleScope.launchWhenResumed {
            withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.use {
                    model.addMany(it.bufferedReader().readText())
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private val exportFeedsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        lifecycleScope.launchWhenResumed {
            withContext(Dispatchers.IO) {
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    it.write(model.exportAsOpml())
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.apply {
            setTitle(R.string.feeds)
            inflateMenu(R.menu.menu_feeds)

            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.importFeeds -> {
                        importFeedsLauncher.launch("*/*")
                    }

                    R.id.exportFeeds -> {
                        exportFeedsLauncher.launch("feeds.opml")
                    }
                }

                true
            }
        }

        binding.apply {
            list.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@FeedsFragment.adapter
                addItemDecoration(ListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (list.canScrollVertically(1) || !list.canScrollVertically(-1)) {
                            fab.show()
                        } else {
                            fab.hide()
                        }
                    }
                })
            }

            lifecycleScope.launchWhenResumed { model.onViewReady() }

            lifecycleScope.launchWhenResumed {
                model.state.collectLatest { state ->
                    list.hide()
                    progress.hide()
                    message.hide()
                    importOpml.hide()
                    fab.hide()

                    when (state) {
                        FeedsViewModel.State.Loading -> {
                            progress.show(animate = true)
                        }

                        is FeedsViewModel.State.Loaded -> {
                            state.result.onSuccess {
                                if (it.isEmpty()) {
                                    message.show(animate = true)
                                    message.text = getString(R.string.you_have_no_feeds)
                                    importOpml.show(animate = true)
                                }

                                list.show()
                                adapter.submitList(it)
                                fab.show()
                            }.onFailure {
                                showErrorDialog(it) {
                                    lifecycleScope.launchWhenResumed { model.reload() }
                                }
                            }
                        }

                        is FeedsViewModel.State.AddingOne -> {
                            progress.show(animate = true)
                        }

                        is FeedsViewModel.State.AddedOne -> {
                            state.result.onSuccess {
                                lifecycleScope.launchWhenResumed { model.reload() }
                            }.onFailure {
                                showErrorDialog(it) {
                                    lifecycleScope.launchWhenResumed { model.reload() }
                                }
                            }
                        }

                        is FeedsViewModel.State.ImportingOpml -> {
                            progress.show(animate = true)
                            message.show(animate = true)

                            state.progress.collectLatest { progress ->
                                message.text = getString(
                                    R.string.importing_feeds_n_of_n,
                                    progress.imported,
                                    progress.total,
                                )
                            }
                        }

                        is FeedsViewModel.State.ImportedOpml -> {
                            when (state.result) {
                                is FeedsViewModel.OpmlImportResult.Imported -> {
                                    val message = buildString {
                                        append(getString(R.string.added_d, state.result.feedsAdded))
                                        append("\n")
                                        append(getString(R.string.exists_d, state.result.feedsUpdated))
                                        append("\n")
                                        append(getString(R.string.failed_d, state.result.feedsFailed))

                                        if (state.result.errors.isNotEmpty()) {
                                            append("\n\n")
                                        }

                                        state.result.errors.forEach {
                                            append(it)

                                            if (state.result.errors.last() != it) {
                                                append("\n\n")
                                            }
                                        }
                                    }

                                    showDialog(
                                        title = getString(R.string.import_title),
                                        message = message,
                                    ) {
                                        lifecycleScope.launchWhenResumed { model.reload() }
                                    }
                                }

                                is FeedsViewModel.OpmlImportResult.FailedToParse -> {
                                    val message = buildString {
                                        append(getString(R.string.opml_file_is_invalid))
                                        append("\n\n")
                                        append(state.result.reason)
                                    }

                                    showErrorDialog(message) {
                                        model.reload()
                                    }
                                }
                            }
                        }

                        is FeedsViewModel.State.Renaming -> {
                            progress.show(animate = true)
                        }

                        is FeedsViewModel.State.Renamed -> {
                            state.result.onSuccess {
                                lifecycleScope.launchWhenResumed { model.reload() }
                            }.onFailure {
                                showErrorDialog(it) {
                                    lifecycleScope.launchWhenResumed { model.reload() }
                                }
                            }
                        }

                        is FeedsViewModel.State.Deleting -> {
                            progress.show(animate = true)
                        }

                        is FeedsViewModel.State.Deleted -> {
                            state.result.onSuccess {
                                lifecycleScope.launchWhenResumed { model.reload() }
                            }.onFailure {
                                showErrorDialog(it) {
                                    lifecycleScope.launchWhenResumed { model.reload() }
                                }
                            }
                        }
                    }
                }
            }

            importOpml.setOnClickListener {
                importFeedsLauncher.launch("*/*")
            }

            fab.setOnClickListener {
                val alert = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.add_feed))
                    .setView(R.layout.dialog_add_feed)
                    .setPositiveButton(R.string.add) { dialogInterface, _ ->
                        val dialog = dialogInterface as AlertDialog
                        val url = dialog.findViewById<TextInputEditText>(R.id.url)?.text.toString()

                        val parsedUrl = runCatching {
                            URI.create(url).toURL()
                        }.getOrElse {
                            val e = Exception(getString(R.string.invalid_url_s, url))
                            showErrorDialog(e)
                            return@setPositiveButton
                        }

                        lifecycleScope.launchWhenResumed { model.addOne(parsedUrl) }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { hideKeyboard() }
                    .show()

                alert.findViewById<View>(R.id.urlLayout)?.requestFocus()

                alert.findViewById<EditText>(R.id.url)?.apply {
                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            alert.dismiss()

                            val parsedUrl = runCatching {
                                URI.create(text.toString()).toURL()
                            }.getOrElse {
                                val e =
                                    Exception(getString(R.string.invalid_url_s, text.toString()))
                                showErrorDialog(e)
                                return@setOnEditorActionListener true
                            }

                            lifecycleScope.launchWhenResumed { model.addOne(parsedUrl) }
                            return@setOnEditorActionListener true
                        }

                        false
                    }
                }

                requireContext().showKeyboard()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hideKeyboard() {
        requireActivity().window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }
}