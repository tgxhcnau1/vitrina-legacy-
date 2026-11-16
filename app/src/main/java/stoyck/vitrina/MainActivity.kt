package stoyck.vitrina

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.observe
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.content_subreddit_list.*
import kotlinx.android.synthetic.main.nav_header_main.*
import stoyck.vitrina.domain.MainViewModel
import stoyck.vitrina.util.DebouncedTextWatcher
import stoyck.vitrina.util.hideKeyboard
import javax.inject.Inject


class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModel: MainViewModel

    // There is only add and they need to be removed eventually
    // to make sure that only one of them exists
    private var minimumImageWidthTextWatcher: TextWatcher? = null
    private var minimumImageHeightTextWatcher: TextWatcher? = null

    private var pendingRestoreJson: String? = null

    companion object {
        private const val REQUEST_CREATE_BACKUP = 4101
        private const val REQUEST_OPEN_BACKUP = 4102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (applicationContext as VitrinaApplication).appComponent
            .inject(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Toolbar
        viewModel.menuState.observe(this) {
            when (it) {
                MainViewModel.MenuState.Default -> renderDefaultState()
                MainViewModel.MenuState.Search -> renderSearchState()
            }
        }

        setupSettings()

        subredditsRecyclerView.onSave = { subreddits ->
            viewModel.saveSubreddits(subreddits)
        }

        subredditsRecyclerView.onMessage = {
            viewModel.setUserMessageAsync(it)
        }

        viewModel.isLoading.observe(this) { loading ->
            mainProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.userMessage.observe(this) { message ->
            if (message != null) {
                showSnackbar(message)
            }
        }

        viewModel.subreddits.observe(this) { subreddits ->
            if (subreddits.isEmpty()) {
                noSubredditsMessage.visibility = View.VISIBLE
                contentSubredditsContainer.visibility = View.GONE
                return@observe
            }

            noSubredditsMessage.visibility = View.GONE
            contentSubredditsContainer.visibility = View.VISIBLE

            subredditsRecyclerView.setData(subreddits)
        }
    }

    private fun toDefaultMenu() {
        hideKeyboard()
        subredditInputText.setText("")
        subredditInputText.clearFocus()
        viewModel.toDefaultMenu()
    }

    private fun toSearchMenu() {
        viewModel.toSearchMenu()
        subredditInputText?.post {
            subredditInputText.requestFocus()
            val imm: InputMethodManager =
                this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(subredditInputText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun submitSubredditInput() {
        val text = subredditInputText.text?.toString()?.trim().orEmpty()
        toDefaultMenu()
        if (text.isNotEmpty()) {
            viewModel.tryAddSubreddit(text)
        }
    }

    private fun setupSettings() {
        shuffleSwitch.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.preferencesState.value!!
            viewModel.updatePreferences(current.copy(shuffle = isChecked))
        }

        over18Switch.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.preferencesState.value!!
            viewModel.updatePreferences(current.copy(isOver18 = isChecked))
        }


        val textWatcherMinWidth = this.minimumImageWidthTextWatcher
        if (textWatcherMinWidth == null) {
            val newTextWatcher = DebouncedTextWatcher(300L) { text ->
                val newValue = text.toIntOrNull() ?: return@DebouncedTextWatcher

                val current = viewModel.preferencesState.value!!
                viewModel.updatePreferences(current.copy(minimumImageWidth = newValue))
            }

            this.minimumImageWidthTextWatcher = newTextWatcher
            imageMinimumWidthInput.addTextChangedListener(newTextWatcher)
        }

        val textWatcherMinHeight = this.minimumImageHeightTextWatcher
        if (textWatcherMinHeight == null) {
            val newTextWatcher = DebouncedTextWatcher(300L) { text ->
                val newValue = text.toIntOrNull() ?: return@DebouncedTextWatcher

                val current = viewModel.preferencesState.value!!
                viewModel.updatePreferences(current.copy(minimumImageHeight = newValue))
            }

            this.minimumImageHeightTextWatcher = newTextWatcher
            imageMinimumHeightInput.addTextChangedListener(newTextWatcher)
        }


        viewModel.preferencesState.observe(this) {
            if (it == null) {
                return@observe
            }

            /**
             * This is some complicated shit:
             *
             * ===
             *
             * Default data: (shuffle: true, over: false)
             * Loaded data in settings: (shuffle: true, over: true)
             *
             * This is a problem, because the shuffle toggle will trigger saves
             *
             * Current issues:
             * - different default data than what is saved
             *      - this is the main issue, because it will copied from the toggle's callback
             *          when saving
             * - async load/save and only 1 thread
             *      - task execution is posted, but the ordering of the coroutines change
             *      - later load in the viewmodel than the first save triggered from checkbox
             * - save is triggered, even when the same value is set in toggle
             */

            if (shuffleSwitch.isChecked != it.shuffle) {
                shuffleSwitch.isChecked = it.shuffle
            }

            if (over18Switch.isChecked != it.isOver18) {
                over18Switch.isChecked = it.isOver18
            }

            if (imageMinimumWidthInput.text?.toString()?.toIntOrNull() != it.minimumImageWidth) {
                imageMinimumWidthInput.setText(it.minimumImageWidth.toString())
            }

            if (imageMinimumHeightInput.text?.toString()?.toIntOrNull() != it.minimumImageHeight) {
                imageMinimumHeightInput.setText(it.minimumImageHeight.toString())
            }
        }

        goToStoreButton.setOnClickListener {
            val appPackageName =
                packageName // getPackageName() from Context or Activity object

            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$appPackageName")
                    )
                )
            } catch (anfe: ActivityNotFoundException) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                    )
                )
            }
        }

        goToGithubButton.setOnClickListener {
            val browserIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/andrasferenczi/vitrina"))
            startActivity(browserIntent)
        }

        backupButton.setOnClickListener {
            startBackupFilePicker()
        }

        restoreButton.setOnClickListener {
            startRestoreFilePicker()
        }
    }

    private fun startBackupFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, getString(R.string.backup_file_name))
        }
        try {
            startActivityForResult(intent, REQUEST_CREATE_BACKUP)
        } catch (e: ActivityNotFoundException) {
            viewModel.notifyBackupFailed()
        }
    }

    private fun startRestoreFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "text/*"))
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_BACKUP)
        } catch (e: ActivityNotFoundException) {
            viewModel.notifyBackupFailed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQUEST_CREATE_BACKUP -> writeBackupTo(uri)
            REQUEST_OPEN_BACKUP -> readBackupFrom(uri)
        }
    }

    private fun writeBackupTo(uri: Uri) {
        val json = viewModel.createBackupJson()
        val count = viewModel.currentSubredditCount()
        Thread {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Could not open backup file")
                viewModel.notifyBackupSaved(count)
            } catch (e: Exception) {
                viewModel.notifyBackupFailed()
            }
        }.start()
    }

    private fun readBackupFrom(uri: Uri) {
        Thread {
            val json = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    // Safety: never load an unlimited file into memory.
                    // A real vitrina+ backup is only a few KB; 5 MB is generous.
                    val maxBytes = 5 * 1024 * 1024
                    val buffer = ByteArray(8192)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) {
                            throw IllegalStateException("Backup file is too large")
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("Could not open backup file")
            } catch (e: Exception) {
                viewModel.notifyBackupFailed()
                return@Thread
            }

            runOnUiThread {
                pendingRestoreJson = json
                AlertDialog.Builder(this)
                    .setTitle(R.string.restore_confirm_title)
                    .setMessage(
                        getString(
                            R.string.restore_confirm_message,
                            viewModel.currentSubredditCount()
                        )
                    )
                    .setPositiveButton(R.string.action_restore_confirm) { _, _ ->
                        pendingRestoreJson?.let { viewModel.restoreFromBackupJson(it) }
                        pendingRestoreJson = null
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    .setNegativeButton(R.string.action_cancel) { _, _ ->
                        pendingRestoreJson = null
                    }
                    .setOnCancelListener { pendingRestoreJson = null }
                    .show()
            }
        }.start()
    }

    private fun renderSearchState() {
        fun setupSearchToolbar() {
            defaultToolbar.visibility = View.GONE
            searchToolbar.let {
                it.visibility = View.VISIBLE

                setSupportActionBar(searchToolbar)
                supportActionBar?.setDisplayShowTitleEnabled(false)
                it.setNavigationIcon(R.drawable.ic_keyboard_backspace)
                it.setNavigationOnClickListener {
                    toDefaultMenu()
                }

                subredditInputText.setOnEditorActionListener { _, actionId, event ->
                    val isImeAction = actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_GO ||
                        actionId == EditorInfo.IME_ACTION_SEND ||
                        actionId == EditorInfo.IME_ACTION_SEARCH
                    val isHardwareEnter = actionId == EditorInfo.IME_NULL &&
                        event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN

                    if (isImeAction || isHardwareEnter) {
                        submitSubredditInput()
                        true
                    } else {
                        false
                    }
                }
            }
        }

        contentMainContainer.visibility = View.VISIBLE

        invalidateOptionsMenu()

        addSubredditFab.visibility = View.GONE

        setupSearchToolbar()
    }

    private fun renderDefaultState() {
        fun setupDefaultToolbar() {
            searchToolbar.visibility = View.GONE

            defaultToolbar.let {
                it.visibility = View.VISIBLE
                setSupportActionBar(it)
                supportActionBar?.setDisplayShowTitleEnabled(true)

                val toggle = ActionBarDrawerToggle(
                    this,
                    drawerLayout,
                    it,
                    R.string.nav_app_bar_open_drawer_description,
                    R.string.nav_header_desc
                )

                /// not add, because this might be called multiple times
                drawerLayout.setDrawerListener(toggle)
                toggle.syncState()

                // AFTER syncState
                it.setNavigationIcon(R.drawable.ic_settings)
            }

            supportActionBar?.title = this.resources.getString(R.string.main_screen_title)
        }

        contentMainContainer.visibility = View.VISIBLE

        invalidateOptionsMenu()

        addSubredditFab.visibility = View.VISIBLE
        addSubredditFab.setOnClickListener {
            toSearchMenu()
        }

        setupDefaultToolbar()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        viewModel.menuState.value?.let {
            when (it) {
                MainViewModel.MenuState.Default -> {
                    menuInflater.inflate(R.menu.main_default, menu)
                    val search = menu.findItem(R.id.action_search)
                    search.setOnMenuItemClickListener {
                        toSearchMenu()
                        true
                    }
                }
                MainViewModel.MenuState.Search -> {
                    menuInflater.inflate(R.menu.main_search, menu)

                    val actionConfirmAdd = menu.findItem(R.id.action_confirm_add_subreddit)
                    actionConfirmAdd.setOnMenuItemClickListener {
                        submitSubredditInput()
                        true
                    }

                    val actionClearInputText = menu.findItem(R.id.action_clear_input_text)
                    actionClearInputText.setOnMenuItemClickListener {
                        if (subredditInputText.text?.toString()?.trim().isNullOrEmpty()) {
                            toDefaultMenu()
                        } else {
                            subredditInputText.setText("")
                        }

                        true
                    }
                }
            }
        }

        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        if (viewModel.menuState.value == MainViewModel.MenuState.Search) {
            toDefaultMenu()
            return
        }

        super.onBackPressed()
    }


    fun showSnackbar(message: String) {
        Snackbar.make(contentMainContainer, message, Snackbar.LENGTH_LONG)
            // .setAction("CLOSE") { }
            // .setActionTextColor(resources.getColor(android.R.color.holo_red_light))
            .show()
    }
}
