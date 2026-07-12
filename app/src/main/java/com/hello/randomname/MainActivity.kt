package com.hello.randomname

import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var tvNameList: TextView
    private lateinit var tvLastName: TextView
    private lateinit var btnGenerate: MaterialButton
    private lateinit var spMingFile: Spinner

    private val generatedNames = mutableListOf<String>()
    private var nameCount = 0

    private lateinit var surnames: Array<String>
    private var givenChars: Array<String> = emptyArray()
    private var currentFile = "ming_通用.txt"

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollView = findViewById(R.id.scrollView)
        tvNameList = findViewById(R.id.tvNameList)
        tvLastName = findViewById(R.id.tvLastName)
        btnGenerate = findViewById(R.id.btnGenerate)
        spMingFile = findViewById(R.id.spMingFile)

        // 从 assets 加载姓氏词库
        surnames = loadWords("xing.txt")

        // 列出所有 ming 文件
        setupFileSelector()

        btnGenerate.setOnClickListener {
            generateAndDisplayName()
        }
    }

    private fun setupFileSelector() {
        val files = assets.list("")?.filter {
            it.startsWith("ming_") && it.endsWith(".txt")
        }?.sorted() ?: emptyList()

        // 提取显示名（去掉 "ming_" 前缀和 ".txt" 后缀）
        val displayNames = files.map { it.removePrefix("ming_").removeSuffix(".txt") }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMingFile.adapter = adapter

        // 选中默认项（通用）
        val defaultIndex = files.indexOf(currentFile).coerceAtLeast(0)
        spMingFile.setSelection(defaultIndex)
        loadGivenChars(currentFile)

        spMingFile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedFile = files[position]
                if (selectedFile != currentFile) {
                    currentFile = selectedFile
                    loadGivenChars(currentFile)
                    resetNameList()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadGivenChars(fileName: String) {
        givenChars = loadWords(fileName)
    }

    private fun resetNameList() {
        generatedNames.clear()
        nameCount = 0
        tvNameList.text = ""
        tvLastName.setText(R.string.name_list_init)
    }

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

    private fun generateAndDisplayName() {
        if (givenChars.isEmpty()) {
            tvLastName.text = "词库为空"
            return
        }

        val name = randomName()
        // 去重保护（极小概率碰撞）
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
