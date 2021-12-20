package settings

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.appreactor.news.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFragmentTest {

    @Test
    fun resumesWithoutCrashes() {
        launchFragmentInContainer<SettingsFragment>(
            themeResId = R.style.Theme_Material3_DynamicColors_DayNight
        )
    }
}