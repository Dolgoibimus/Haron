package com.vamp.haron.presentation.editor

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource
import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

data class EditorTheme(
    val id: String,
    val displayName: String,
    val fileName: String,
    val isDark: Boolean
)

object SyntaxHighlightHelper {

    private val initialized = AtomicBoolean(false)
    private val initStarted = AtomicBoolean(false)
    private val initLatch = CountDownLatch(1)

    /** All available themes */
    val themes = listOf(
        EditorTheme("darcula", "Darcula", "darcula.json", true),
        EditorTheme("dracula", "Dracula", "dracula.json", true),
        EditorTheme("monokai", "Monokai", "monokai.json", true),
        EditorTheme("nord", "Nord", "nord.json", true),
        EditorTheme("one-dark-pro", "One Dark Pro", "one-dark-pro.json", true),
        EditorTheme("github-dark", "GitHub Dark", "github-dark.json", true),
        EditorTheme("tokyo-night", "Tokyo Night", "tokyo-night.json", true),
        EditorTheme("solarized-dark", "Solarized Dark", "solarized-dark.json", true),
        EditorTheme("quietlight", "Quiet Light", "quietlight.json", false),
        EditorTheme("github-light", "GitHub Light", "github-light.json", false),
        EditorTheme("solarized-light", "Solarized Light", "solarized-light.json", false),
    )

    /** Current theme ID */
    @Volatile
    var currentThemeId: String = "darcula"
        private set

    private val extensionToScope = mapOf(
        "html" to "text.html.basic", "htm" to "text.html.basic",
        "css" to "source.css",
        "js" to "source.js", "mjs" to "source.js",
        "ts" to "source.ts",
        "java" to "source.java",
        "kt" to "source.kotlin", "kts" to "source.kotlin",
        "c" to "source.c", "h" to "source.c",
        "cpp" to "source.cpp", "cc" to "source.cpp", "cxx" to "source.cpp", "hpp" to "source.cpp",
        "cs" to "source.cs",
        "go" to "source.go",
        "rs" to "source.rust",
        "swift" to "source.swift",
        "py" to "source.python",
        "rb" to "source.ruby",
        "php" to "source.php",
        "lua" to "source.lua",
        "sh" to "source.shell", "bash" to "source.shell", "zsh" to "source.shell",
        "bat" to "source.batchfile", "cmd" to "source.batchfile",
        "ps1" to "source.powershell",
        "json" to "source.json",
        "xml" to "text.xml",
        "yaml" to "source.yaml", "yml" to "source.yaml",
        "toml" to "source.toml",
        "ini" to "source.ini",
        "properties" to "source.properties",
        "md" to "text.html.markdown", "markdown" to "text.html.markdown",
        "sql" to "source.sql",
        "dart" to "source.dart",
        "groovy" to "source.groovy", "gradle" to "source.groovy",
        "diff" to "source.diff", "patch" to "source.diff",
        "log" to "text.log",
    )

    fun init(context: Context) {
        if (!initStarted.compareAndSet(false, true)) {
            initLatch.await()
            return
        }
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.assets)
            )
            // Load all themes
            for (theme in themes) {
                try {
                    val source = IThemeSource.fromInputStream(
                        context.assets.open("textmate/${theme.fileName}"),
                        theme.fileName, null
                    )
                    ThemeRegistry.getInstance().loadTheme(ThemeModel(source, theme.id))
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "SyntaxHighlight: failed to load theme ${theme.id}: ${e.message}")
                }
            }
            ThemeRegistry.getInstance().setTheme("darcula")

            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            initialized.set(true)
            EcosystemLogger.d(HaronConstants.TAG, "SyntaxHighlight: initialized, ${themes.size} themes loaded")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "SyntaxHighlight: init failed: ${e.message}")
        } finally {
            initLatch.countDown()
        }
    }

    @Volatile
    private var preparedLanguage: TextMateLanguage? = null
    @Volatile
    private var preparedColorScheme: TextMateColorScheme? = null

    fun prepareLanguage(fileName: String) {
        if (!initialized.get()) return
        try {
            val ext = resolveExtension(fileName)
            val scopeName = extensionToScope[ext] ?: return
            preparedLanguage = TextMateLanguage.create(scopeName, true)
            preparedColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            EcosystemLogger.d(HaronConstants.TAG, "SyntaxHighlight: prepared scope=$scopeName ext=$ext")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "SyntaxHighlight: prepare failed: ${e.message}")
        }
    }

    fun applyPrepared(editor: CodeEditor, isDark: Boolean): Boolean {
        val lang = preparedLanguage ?: return false
        val scheme = preparedColorScheme ?: return false
        try {
            val autoTheme = if (isDark) "darcula" else "quietlight"
            val themeId = currentThemeId.ifEmpty { autoTheme }
            ThemeRegistry.getInstance().setTheme(themeId)
            editor.colorScheme = scheme
            editor.setEditorLanguage(lang)
            EcosystemLogger.d(HaronConstants.TAG, "SyntaxHighlight: applied theme=$themeId")
            return true
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "SyntaxHighlight: apply failed: ${e.message}")
            return false
        }
    }

    /**
     * Switch theme on an existing editor. Can be called from main thread
     * (only sets theme + recreates color scheme, no grammar parsing).
     */
    fun switchTheme(editor: CodeEditor, themeId: String): Boolean {
        if (!initialized.get()) return false
        try {
            currentThemeId = themeId
            ThemeRegistry.getInstance().setTheme(themeId)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            EcosystemLogger.d(HaronConstants.TAG, "SyntaxHighlight: switched to theme=$themeId")
            return true
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "SyntaxHighlight: switchTheme failed: ${e.message}")
            return false
        }
    }

    fun applyHighlighting(editor: CodeEditor, fileName: String, isDark: Boolean): Boolean {
        if (!initialized.get()) return false
        try {
            val autoTheme = if (isDark) "darcula" else "quietlight"
            val themeId = currentThemeId.ifEmpty { autoTheme }
            ThemeRegistry.getInstance().setTheme(themeId)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())

            val ext = resolveExtension(fileName)
            val scopeName = extensionToScope[ext] ?: return false
            editor.setEditorLanguage(TextMateLanguage.create(scopeName, true))
            EcosystemLogger.d(HaronConstants.TAG, "SyntaxHighlight: scope=$scopeName theme=$themeId")
            return true
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "SyntaxHighlight: apply failed: ${e.message}")
            return false
        }
    }

    fun getLanguageName(fileName: String): String? {
        val ext = fileName.lowercase().substringAfterLast('.', "")
        return when (ext) {
            "kt", "kts" -> "Kotlin"; "java" -> "Java"; "py" -> "Python"
            "js", "mjs" -> "JavaScript"; "ts" -> "TypeScript"
            "html", "htm" -> "HTML"; "css" -> "CSS"; "json" -> "JSON"
            "xml" -> "XML"; "yaml", "yml" -> "YAML"; "sql" -> "SQL"
            "c", "h" -> "C"; "cpp", "cc", "cxx", "hpp" -> "C++"
            "cs" -> "C#"; "go" -> "Go"; "rs" -> "Rust"; "swift" -> "Swift"
            "rb" -> "Ruby"; "php" -> "PHP"; "lua" -> "Lua"
            "sh", "bash", "zsh" -> "Shell"; "md", "markdown" -> "Markdown"
            "dart" -> "Dart"; "groovy", "gradle" -> "Groovy"
            "toml" -> "TOML"; "diff", "patch" -> "Diff"; "log" -> "Log"
            else -> null
        }
    }

    private fun resolveExtension(fileName: String): String {
        return fileName.lowercase().let { name ->
            when {
                name.endsWith(".gradle.kts") -> "kts"
                name == "makefile" || name == "dockerfile" -> name
                else -> name.substringAfterLast('.', "")
            }
        }
    }
}
