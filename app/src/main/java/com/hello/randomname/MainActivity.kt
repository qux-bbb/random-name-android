package com.hello.randomname

import android.content.res.AssetManager
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var tvNameList: TextView
    private lateinit var tvLastName: TextView
    private lateinit var btnGenerate: MaterialButton

    private val generatedNames = mutableListOf<String>()
    private var nameCount = 0

    private lateinit var surnames: Array<String>
    private lateinit var givenChars: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollView = findViewById(R.id.scrollView)
        tvNameList = findViewById(R.id.tvNameList)
        tvLastName = findViewById(R.id.tvLastName)
        btnGenerate = findViewById(R.id.btnGenerate)

        // 从 assets 加载词库
        surnames = loadWords("xing.txt")
        givenChars = loadWords("ming.txt")

        btnGenerate.setOnClickListener {
            generateAndDisplayName()
        }
    }

    private fun loadWords(fileName: String): Array<String> {
        return try {
            val inputStream = assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val text = reader.readText()
            text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.toTypedArray()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyArray()
        }
    }

    private fun generateAndDisplayName() {
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
        val givenLength = if (Math.random() > 0.5) 2 else 1
        val given = (1..givenLength).joinToString("") { givenChars.random() }
        return "$surname:$given"
    }
}
