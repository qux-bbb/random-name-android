package com.hello.randomname

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var tvNameList: TextView
    private lateinit var tvLastName: TextView
    private lateinit var btnGenerate: MaterialButton
    private lateinit var btnManage: MaterialButton
    private lateinit var spMingFile: Spinner

    private val generatedNames = mutableListOf<String>()
    private var nameCount = 0

    private lateinit var surnames: Array<String>
    private var givenChars: Array<String> = emptyArray()
    private var currentFileName = "ming_通用.txt"

    private lateinit var libDir: File
    private var fileList: List<String> = emptyList()
    private lateinit var prefs: android.content.SharedPreferences

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private const val LIB_DIR_NAME = "ming_library"
        private const val PREF_DEFAULT_FILE = "default_ming_file"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollView = findViewById(R.id.scrollView)
        tvNameList = findViewById(R.id.tvNameList)
        tvLastName = findViewById(R.id.tvLastName)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnManage = findViewById(R.id.btnManage)
        spMingFile = findViewById(R.id.spMingFile)

        libDir = File(filesDir, LIB_DIR_NAME)
        prefs = getSharedPreferences("random_name_prefs", MODE_PRIVATE)
        currentFileName = prefs.getString(PREF_DEFAULT_FILE, "ming_通用.txt") ?: "ming_通用.txt"
        ensureLibraryFiles()

        // 从内部存储加载姓氏词库
        surnames = loadWordsFromPath(File(libDir, "xing.txt").absolutePath)

        setupFileSelector()
        setupManageButton()

        btnGenerate.setOnClickListener {
            generateAndDisplayName()
        }
    }

    private fun ensureLibraryFiles() {
        if (!libDir.exists()) libDir.mkdirs()

        // 首次启动：从 assets 复制默认文件到内部存储
        val assetFiles = assets.list("")?.filter {
            it.endsWith(".txt")
        } ?: emptyList()

        for (name in assetFiles) {
            val target = File(libDir, name)
            if (!target.exists()) {
                try {
                    assets.open(name).use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 把原始 ming.txt 也复制一份带前缀的名称（如果没有 ming_ 版本的话）
        val rawMing = File(libDir, "ming.txt")
        val prefixedMing = File(libDir, "ming_原始.txt")
        if (rawMing.exists() && !prefixedMing.exists()) {
            rawMing.copyTo(prefixedMing)
        }
    }

    /** 列出内部存储中所有 ming_ 开头的文件 */
    private fun getMingFiles(): List<String> {
        return libDir.listFiles()
            ?.filter { it.name.startsWith("ming_") && it.name.endsWith(".txt") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /** 从文件名提取显示名（ming_通用.txt → 通用） */
    private fun displayName(fileName: String): String {
        return fileName.removePrefix("ming_").removeSuffix(".txt")
    }

    // ── Spinner 文件选择器 ──

    private fun setupFileSelector() {
        spMingFile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedFile = fileList[position]
                if (selectedFile != currentFileName) {
                    currentFileName = selectedFile
                    prefs.edit().putString(PREF_DEFAULT_FILE, currentFileName).apply()
                    loadGivenChars(File(libDir, currentFileName).absolutePath)
                    resetNameList()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        refreshFileList()
        // 显式加载当前选中文件（Spinner 首次选中可能不触发 onItemSelected）
        loadGivenChars(File(libDir, currentFileName).absolutePath)
    }

    private fun refreshFileList() {
        fileList = getMingFiles()
        val displayNames = fileList.map { displayName(it) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMingFile.adapter = adapter

        // 如果保存的默认文件不存在，回退到第一个可用项
        if (currentFileName !in fileList) {
            currentFileName = fileList.firstOrNull() ?: currentFileName
        }
        val idx = fileList.indexOf(currentFileName).coerceAtLeast(0)
        spMingFile.setSelection(idx)
    }

    // ── 文件管理 ──

    private fun setupManageButton() {
        btnManage.setOnClickListener { showManageDialog() }
    }

    private fun showManageDialog() {
        val items = getMingFiles().map { name ->
            val file = File(libDir, name)
            val count = loadWordsCount(file.absolutePath)
            "${displayName(name)}（${count}字）"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("管理名字库")
            .setItems(items) { _: DialogInterface, _: Int -> /* 暂不开放点击编辑 */ }
            .setPositiveButton("新建") { _, _ -> showCreateDialog() }
            .setNeutralButton("删除") { _, _ -> showDeleteDialog() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showCreateDialog() {
        val editText = EditText(this).apply {
            hint = "输入名字用字，空格分隔"
            setText("")
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("新建名字库")
            .setMessage("输入汉字，用空格分隔（例如：伟 强 芳 婷）")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val nameEdit = EditText(this).apply {
                    hint = "文件名（如：我的字库）"
                    setText("")
                    setPadding(48, 32, 48, 32)
                }
                AlertDialog.Builder(this)
                    .setTitle("文件名")
                    .setView(nameEdit)
                    .setPositiveButton("确定") { _, _ ->
                        val display = nameEdit.text.toString().trim()
                        if (display.isEmpty()) {
                            Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        val fileName = "ming_$display.txt"
                        val file = File(libDir, fileName)
                        if (file.exists()) {
                            Toast.makeText(this, "文件已存在", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        file.writeText(text, Charsets.UTF_8)
                        refreshFileList()
                        // 自动切到新文件
                        val idx = fileList.indexOf(fileName).coerceAtLeast(0)
                        spMingFile.setSelection(idx)
                        currentFileName = fileName
                        loadGivenChars(file.absolutePath)
                        resetNameList()
                        Toast.makeText(this, "已创建「${display}」", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDialog() {
        val files = getMingFiles()
        val names = files.map { displayName(it) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择要删除的名字库")
            .setItems(names) { _, which ->
                val toDelete = files[which]
                if (toDelete == currentFileName) {
                    Toast.makeText(this, "不能删除当前正在使用的文件", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定删除「${displayName(toDelete)}」？")
                    .setPositiveButton("删除") { _, _ ->
                        File(libDir, toDelete).delete()
                        refreshFileList()
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("返回", null)
            .show()
    }

    // ── 核心功能 ──

    private fun loadGivenChars(filePath: String) {
        givenChars = loadWordsFromPath(filePath)
    }

    private fun resetNameList() {
        generatedNames.clear()
        nameCount = 0
        tvNameList.text = ""
        tvLastName.setText(R.string.name_list_init)
    }

    /** 从 assets 加载文件 */
    private fun loadWords(fileName: String): Array<String> {
        return try {
            val inputStream = assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val text = reader.readText()
            text.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }.toTypedArray()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyArray()
        }
    }

    /** 从绝对路径加载文件 */
    private fun loadWordsFromPath(filePath: String): Array<String> {
        return try {
            val text = File(filePath).readText(Charsets.UTF_8)
            text.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }.toTypedArray()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyArray()
        }
    }

    /** 只统计字数，不加载到内存 */
    private fun loadWordsCount(filePath: String): Int {
        return try {
            val text = File(filePath).readText(Charsets.UTF_8)
            text.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }
        } catch (e: Exception) {
            0
        }
    }

    private fun generateAndDisplayName() {
        if (givenChars.isEmpty()) {
            tvLastName.text = "词库为空"
            return
        }

        val name = randomName()
        val uniqueName = if (name in generatedNames) {
            var newName: String
            do {
                newName = randomName()
            } while (newName in generatedNames)
            newName
        } else {
            name
        }

        nameCount++
        generatedNames.add(uniqueName)
        tvLastName.text = uniqueName
        tvNameList.append("$nameCount. $uniqueName\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun randomName(): String {
        val surname = surnames.random()
        val givenLength = if (Random.nextBoolean()) 2 else 1
        val given = (1..givenLength).joinToString("") { givenChars.random() }
        return "$surname:$given"
    }
}
