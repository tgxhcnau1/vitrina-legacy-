package stoyck.vitrina.domain

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import kotlinx.coroutines.*
import stoyck.vitrina.R
import stoyck.vitrina.domain.preferences.PreferencesData
import stoyck.vitrina.domain.usecase.*
import stoyck.vitrina.persistence.data.PersistedSubredditData
import stoyck.vitrina.persistence.data.VitrinaBackupCodec
import stoyck.vitrina.util.Debouncer
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainViewModel @Inject constructor(
    private val context: Context,
    private val tryAddSubredditUseCase: TryAddSubredditUseCase,
    private val loadSettingsUseCase: LoadSettingsUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase,
    private val loadSubredditsUseCase: LoadSubredditsUseCase,
    private val saveSubredditsUseCase: SaveSubredditsUseCase
) {

    private val job = Dispatchers.IO + SupervisorJob()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        GlobalScope.launch {
            showMessageAndLog(throwable)
        }
    }

    private val scope = CoroutineScope(job + exceptionHandler)

    enum class MenuState {
        Default,
        Search;
    }

    private val _preferencesState: MutableLiveData<PreferencesData?> =
        MutableLiveData(null)

    val preferencesState: LiveData<PreferencesData?> = _preferencesState

    private val _menuState: MutableLiveData<MenuState> = MutableLiveData(MenuState.Default)

    val menuState: LiveData<MenuState> = _menuState

    private val _subreddits: MutableLiveData<List<PersistedSubredditData>> = MutableLiveData(
        emptyList()
    )

    val subreddits: LiveData<List<PersistedSubredditData>> = _subreddits

    private val _userMessage: MutableLiveData<String?> = MutableLiveData(null)

    val userMessage: LiveData<String?> = _userMessage

    private val userMessageClearupDebouncer = Debouncer(delayMillis = 100L)

    fun setUserMessageAsync(message: String?) {
        scope.launch {
            setUserMessage(message)
        }
    }

    private suspend fun setUserMessage(message: String?) {
        withContext(Dispatchers.Main) {
            _userMessage.value = message

            if (message != null) {
                userMessageClearupDebouncer {
                    _userMessage.value = null
                }
            }
        }
    }

    private val _loadingCount = MutableLiveData(0)

    val isLoading: LiveData<Boolean> = _loadingCount.map { it!! > 0 }

    private suspend fun withLoading(
        block: suspend () -> Unit
    ) {
        try {
            withContext(Dispatchers.Main) {
                _loadingCount.value = _loadingCount.value!! + 1
            }

            block()
        } catch (e: Exception) {
            throw e
        } finally {
            withContext(Dispatchers.Main) {
                _loadingCount.value = _loadingCount.value!! - 1
            }
        }
    }

    private suspend fun showMessageAndLog(exception: Throwable): Boolean {
        when (exception) {
            is UserReadableException -> {
                setUserMessage(exception.userReadableMessage)
            }
            is UnknownHostException -> {
                val noInternet = context.getString(R.string.error_no_internet)
                setUserMessage(noInternet)
            }
            else -> {
                val noIdeaError = context.getString(R.string.error_no_idea)
                setUserMessage(noIdeaError)
            }
        }

        return true
    }

    init {
        loadSettings()
        loadSubreddits()
    }

    fun toDefaultMenu() {
        _menuState.value = MenuState.Default
    }

    fun toSearchMenu() {
        _menuState.value = MenuState.Search
    }

    private fun loadSettings() {
        scope.launch {
            val data = loadSettingsUseCase()

            withContext(Dispatchers.Main) {
                _preferencesState.value = data
            }
        }
    }

    private fun loadSubreddits() {
        scope.launch {
            val data = loadSubredditsUseCase()

            withContext(Dispatchers.Main) {
                this@MainViewModel._subreddits.value = data
            }
        }
    }

    /** Backup: JSON of all subreddits/users + their minimum upvotes. */
    fun createBackupJson(): String {
        return VitrinaBackupCodec.encode(_subreddits.value ?: emptyList())
    }

    fun currentSubredditCount(): Int = _subreddits.value?.size ?: 0

    /** Restore: replaces the whole current list with the backup file. */
    fun restoreFromBackupJson(json: String) {
        scope.launch {
            try {
                val restored = VitrinaBackupCodec.decode(json)
                saveSubredditsUseCase(restored)

                withContext(Dispatchers.Main) {
                    _subreddits.value = restored
                    setUserMessage(
                        context.getString(R.string.message_backup_restored, restored.size)
                    )
                }
            } catch (e: Exception) {
                setUserMessage(context.getString(R.string.error_invalid_backup_file))
            }
        }
    }

    fun notifyBackupSaved(count: Int) {
        setUserMessageAsync(context.getString(R.string.message_backup_saved, count))
    }

    fun notifyBackupFailed() {
        setUserMessageAsync(context.getString(R.string.error_backup_failed))
    }

    fun saveSubreddits(subreddits: List<PersistedSubredditData>) {
        scope.launch {
            saveSubredditsUseCase(subreddits)

            withContext(Dispatchers.Main) {
                this@MainViewModel._subreddits.value = subreddits
            }
        }
    }

    fun updatePreferences(data: PreferencesData) {
        scope.launch {
            saveSettingsUseCase(data)

            withContext(Dispatchers.Main) {
                _preferencesState.value = data
            }
        }
    }

    fun tryAddSubreddit(subreddit: String) {
        scope.launch {
            withLoading {
                val result = tryAddSubredditUseCase(
                    requestedSubredditName = subreddit
                )

                val exception = result.exceptionOrNull()

                if (exception != null) {
                    showMessageAndLog(exception)
                    return@withLoading
                }

                val (addedSubreddit, subreddits) = result.getOrNull()
                    ?: throw RuntimeException("Should not be null if success - failure handled earlier")

                withContext(Dispatchers.Main) {
                    _subreddits.value = subreddits

                    val successMessage =
                        context.getString(R.string.message_subreddit_successfully_added)
                    val displayLabel = if (addedSubreddit.name.startsWith("u/", ignoreCase = true)) {
                        "/${addedSubreddit.name}"
                    } else {
                        "/r/${addedSubreddit.name}"
                    }
                    setUserMessage(successMessage.format(displayLabel))
                }
            }
        }
    }

}
