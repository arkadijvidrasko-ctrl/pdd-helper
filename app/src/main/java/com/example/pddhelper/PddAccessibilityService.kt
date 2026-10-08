package com.example.pddhelper

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class PddAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var dotView: View? = null
    private var questionMap: Map<String, String> = emptyMap()

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        loadDatabase()
    }

    private fun loadDatabase() {
        try {
            val inputStream = assets.open("questions.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<PddQuestion>>() {}.type
            val questions: List<PddQuestion> = Gson().fromJson(reader, type)
            questionMap = questions.associate {
                normalizeText(it.question) to normalizeText(it.correct_answer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-zа-я0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        traverseNode(rootNode, allNodes)
        val screenTexts = allNodes.mapNotNull { it.text?.toString() }
        var correctAnswer = ""

        for (text in screenTexts) {
            val normalized = normalizeText(text)
            if (questionMap.containsKey(normalized)) {
                correctAnswer = questionMap[normalized] ?: ""
                break
            }
        }

        if (correctAnswer.isNotEmpty()) {
            val correctNode = allNodes.find {
                normalizeText(it.text?.toString() ?: "") == correctAnswer
            }
            if (correctNode != null) {
                val rect = Rect()
                correctNode.getBoundsInScreen(rect)
                showDot(rect)
            }
        } else {
            hideDot()
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, nodes: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        nodes.add(node)
        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), nodes)
        }
    }

    private fun showDot(rect: Rect) {
        if (dotView == null) {
            dotView = View(this).apply {
                setBackgroundColor(android.graphics.Color.GREEN)
            }
            val params = WindowManager.LayoutParams(
                20, 20,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            windowManager.addView(dotView, params)
        }
        val params = dotView?.layoutParams as WindowManager.LayoutParams
        params.x = rect.left - 30
        params.y = rect.centerY() - 10
        windowManager.updateViewLayout(dotView, params)
    }

    private fun hideDot() {
        if (dotView != null) {
            windowManager.removeView(dotView)
            dotView = null
        }
    }

    override fun onInterrupt() { hideDot() }
    override fun onDestroy() { super.onDestroy(); hideDot() }
}

data class PddQuestion(
    val question: String,
    val correct_answer: String
)
