package si.hehe.iscp

import android.annotation.TargetApi
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.preference.*
import android.preference.Preference
import android.view.MenuItem
import org.jetbrains.anko.*
import java.text.MessageFormat
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

        if (!defaultSharedPreferences.contains(getString(R.string.pref_uuid))) {
            val ssh = SSH()
            val sshKeys = ssh.generateSSHKeys()
            val keys = ssh.serializeKeys(sshKeys)
            with (defaultSharedPreferences.edit()) {
                putString(getString(R.string.pref_ssh_keys), keys)
                remove(getString(R.string.pref_password))
                putString(getString(R.string.pref_uuid), UUID.randomUUID().toString())
                apply()
            }
            toast(getString(R.string.toast_prefs_initialized))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (title == getString(R.string.app_name)) { // back on main screen exits
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
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_hostname)))
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_port)))
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_username)))
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_path)))
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_url_prefix)))
        }

        override fun onPreferenceTreeClick(preferenceScreen: PreferenceScreen?, preference: Preference?): Boolean {

            when (preference?.key) {

                getString(R.string.pref_ssh_keys) -> {
                    val ssh = SSH()
                    val sshKeys = ssh.generateSSHKeys()
                    val keys = ssh.serializeKeys(sshKeys)
                    with (defaultSharedPreferences.edit()) {
                        putString(getString(R.string.pref_ssh_keys), keys)
                        apply()
                    }
                    toast(getString(R.string.toast_generate_keys))
                }

                getString(R.string.pref_copy_ssh_pubkey) -> {
                    val ssh = SSH()
                    val keys = ssh.deserializeKeys(
                            defaultSharedPreferences.getString(
                                    getString(R.string.pref_ssh_keys), "")!!)
                    val uuid = defaultSharedPreferences.getString(getString(R.string.pref_uuid), "")!!
                    val pubkey = ssh.extractSSHPublicKey(keys, uuid)
                    val clipboard: ClipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.primaryClip = ClipData.newPlainText("sshpubkey", pubkey)
                    toast(getString(R.string.toast_ssh_key_copied))
                }

                getString(R.string.pref_test_ssh) -> {
                    val prefs = defaultSharedPreferences
                    val host = prefs.getString(getString(R.string.pref_hostname), "")!!
                    val port = prefs.getString(getString(R.string.pref_port), "22")!!.toInt()
                    val username = prefs.getString(getString(R.string.pref_username), "")!!
                    val password = prefs.getString(getString(R.string.pref_password), "")!!
                    val keys = prefs.getString(getString(R.string.pref_ssh_keys), "")!!
                    val ssh = SSH()
                    if (keys.isBlank() && password.isBlank()) {
                        longToast(getString(R.string.toast_missing_auth_data))
                    } else if (!password.isBlank()) {
                        doAsync {
                            uiThread { toast(getString(R.string.toast_verifying_password)) }
                            try {
                                ssh.testSSHPassword(host, port, username, password)
                                uiThread { toast(getString(R.string.toast_password_verified)) }
                            } catch (e: Exception) {
                                uiThread {
                                    toast(MessageFormat.format(
                                            getString(R.string.toast_error), e.message))
                                }
                            }
                        }
                    } else {
                        doAsync {
                            val sshKeys = ssh.deserializeKeys(keys)
                            uiThread { toast(getString(R.string.toast_verifying_key)) }
                            try {
                                ssh.testSSHKeys(host, port, username, sshKeys)
                                uiThread { toast(getString(R.string.toast_key_verified)) }
                            } catch (e: Exception) {
                                uiThread {
                                    toast(MessageFormat.format(
                                            getString(R.string.toast_error), e.message))
                                }
                            }
                        }
                    }
                }

                getString(R.string.pref_setup_ssh_key) -> {
                    val prefs = defaultSharedPreferences
                    val host = prefs.getString(getString(R.string.pref_hostname), "")!!
                    val port = prefs.getString(getString(R.string.pref_port), "22")!!.toInt()
                    val username = prefs.getString(getString(R.string.pref_username), "")!!
                    val password = prefs.getString(getString(R.string.pref_password), "")!!
                    val keys = prefs.getString(getString(R.string.pref_ssh_keys), "")!!
                    val ssh = SSH()
                    if (password.isBlank()) {
                        toast(getString(R.string.toast_password_required))
                    } else {
                        doAsync {
                            try {
                                ssh.testSSHPassword(host, port, username, password)
                                val sshKeys = ssh.deserializeKeys(keys)
                                with(prefs.edit()) {
                                    remove(getString(R.string.pref_password))
                                    apply()
                                }
                                uiThread { toast(getString(R.string.toast_setup_ssh_keys)) }
                                try {
                                    ssh.setupSSHKeyAccess(host, port, username, password, sshKeys,
                                            prefs.getString(getString(R.string.pref_uuid), "")!!)
                                    ssh.testSSHKeys(host, port, username, sshKeys)
                                    uiThread { toast(getString(R.string.toast_ssh_key_setup_success)) }
                                } catch (e: Exception) {
                                    uiThread {
                                        toast(MessageFormat.format(
                                                getString(R.string.toast_error), e.message))
                                    }
                                }
                            } catch (e: Exception) {
                                uiThread {
                                    toast(MessageFormat.format(
                                            getString(R.string.toast_password_error), e.message))
                                }
                            }
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
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_image_size)))
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_image_resize_type)))
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_image_quality)))
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
            if (preference is ListPreference) {
                preference.summary = preference.entries.getOrElse(
                        preference.findIndexOfValue(stringValue)) { "default" }
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

