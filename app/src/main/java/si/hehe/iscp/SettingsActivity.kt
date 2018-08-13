package si.hehe.iscp

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.preference.*
import android.preference.Preference
import android.view.MenuItem
import org.jetbrains.anko.*
import java.util.*


/**
 * A [PreferenceActivity] that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 *
 * See [Android Design: Settings](http://developer.android.com/design/patterns/settings.html)
 * for design guidelines and the [Settings API Guide](http://developer.android.com/guide/topics/ui/settings.html)
 * for more information on developing a Settings UI.
 */
class SettingsActivity : AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()

        if (!defaultSharedPreferences.contains("uuid")) {
            with (defaultSharedPreferences.edit()) {
                putString("uuid", UUID.randomUUID().toString())
                apply()
            }
            toast("initialized")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (title == "iscp") {
            val exit = Intent(Intent.ACTION_MAIN)
            exit.addCategory(Intent.CATEGORY_HOME)
            exit.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (Build.VERSION.SDK_INT > 17) {
                finishAffinity()
            }
            else {
                finish()
            }
            startActivity(exit)
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Set up the [android.app.ActionBar], if the API is available.
     */
    private fun setupActionBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * {@inheritDoc}
     */
    override fun onIsMultiPane(): Boolean {
        return isXLargeTablet(this)
    }

    /**
     * {@inheritDoc}
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    override fun onBuildHeaders(target: List<PreferenceActivity.Header>) {
        loadHeadersFromResource(R.xml.pref_headers, target)
    }

    override fun isValidFragment(fragmentName: String): Boolean {
        return HostSettingsFragment::class.java.name == fragmentName
                || ImageSettingsFragment::class.java.name == fragmentName
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    class HostSettingsFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_host)
            setHasOptionsMenu(true)

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("hostname"))
            bindPreferenceSummaryToValue(findPreference("port"))
            bindPreferenceSummaryToValue(findPreference("username"))
            bindPreferenceSummaryToValue(findPreference("path"))
            bindPreferenceSummaryToValue(findPreference("url_prefix"))

        }

        override fun onPreferenceTreeClick(preferenceScreen: PreferenceScreen?, preference: Preference?): Boolean {
            if (preference?.key == "forget_keys") {
                val prefs = defaultSharedPreferences
                with (prefs.edit()) {
                    remove("auth_ssh_key")
                    remove("password")
                    apply()
                }
                toast("Removed keys and password.")
            }
            else if (preference?.key == "test") {
                val prefs = defaultSharedPreferences
                val host = prefs.getString("hostname", "")!!
                val port = prefs.getString("port", "22")!!.toInt()
                val username = prefs.getString("username", "")!!
                val password = prefs.getString("password", "")!!
                var keys = prefs.getString("auth_ssh_key", "")!!
                val ssh = SSH()
                if (keys.isBlank() && password.isBlank()) {
                    longToast("You must set the password (it is only used to set up the public key authentication and then removed from settings).")
                } else if (!password.isBlank() && keys.isBlank()) {
                    doAsync {
                        val sshKeys = ssh.generateSSHKeys()
                        with(prefs.edit()) {
                            keys = ssh.serializeKeys(sshKeys)
                            putString("auth_ssh_key", keys)
                            remove("password")
                            apply()
                        }
                        uiThread { toast("Setting up ssh auth...") }
                        try {
                            ssh.setupSSHAuth(host, port, username, password, sshKeys, prefs.getString("uuid", "")!!)
                        } catch (e: Exception) {
                            uiThread { toast("Setup failed: ${e.message}") }
                        }
                    }
                }
                if (!keys.isBlank()) {
                    doAsync {
                        val sshKeys = ssh.deserializeKeys(keys)
                        uiThread { toast("Verifying...") }
                        try {
                            ssh.testSSHConnection(host, port, username, sshKeys)
                        } catch (e: Exception) {
                            uiThread { toast("Verification failed: ${e.message}") }
                        } finally {
                            uiThread { toast("SSH public key authentication successful.") }
                        }
                    }
                }
            }
            return super.onPreferenceTreeClick(preferenceScreen, preference)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            val id = item.itemId
            if (id == android.R.id.home) {
                startActivity(Intent(activity, SettingsActivity::class.java))
                return true
            }
            return super.onOptionsItemSelected(item)
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    class ImageSettingsFragment : PreferenceFragment() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_image)
            setHasOptionsMenu(true)

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("image_size"))
            bindPreferenceSummaryToValue(findPreference("image_crop"))
            bindPreferenceSummaryToValue(findPreference("image_quality"))
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            val id = item.itemId
            if (id == android.R.id.home) {
                startActivity(Intent(activity, SettingsActivity::class.java))
                return true
            }
            return super.onOptionsItemSelected(item)
        }

    }

    companion object {

        /**
         * A preference value change listener that updates the preference's summary
         * to reflect its new value.
         */
        private val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
            val stringValue = value.toString()
            if (preference::class == ListPreference::class) {
                val lp = preference as ListPreference
                preference.summary = lp.entries.getOrElse(lp.findIndexOfValue(stringValue)) { "default" }
            } else {
                preference.summary = stringValue
            }
            true
        }

        /**
         * Helper method to determine if the device has an extra-large screen. For
         * example, 10" tablets are extra-large.
         */
        private fun isXLargeTablet(context: Context): Boolean {
            return context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_XLARGE
        }

        /**
         * Binds a preference's summary to its value. More specifically, when the
         * preference's value is changed, its summary (line of text below the
         * preference title) is updated to reflect the value. The summary is also
         * immediately updated upon calling this method. The exact display format is
         * dependent on the type of preference.

         * @see .sBindPreferenceSummaryToValueListener
         */
        private fun bindPreferenceSummaryToValue(preference: Preference) {
            // Set the listener to watch for value changes.
            preference.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener

            // Trigger the listener immediately with the preference's
            // current value.
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.context)
                            .getString(preference.key, ""))
        }
    }
}

