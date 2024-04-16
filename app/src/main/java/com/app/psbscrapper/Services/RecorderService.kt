package com.app.PSBScrapper.Services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.app.PSBScrapper.ApiManager.ApiManager
import com.app.PSBScrapper.Config
import com.app.PSBScrapper.MainActivity
import com.app.PSBScrapper.Utils.AES
import com.app.PSBScrapper.Utils.AccessibilityUtil
import com.app.PSBScrapper.Utils.AutoRunner
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Locale


class RecorderService : AccessibilityService() {
    private val ticker = AutoRunner(this::initialStage)
    private var appNotOpenCounter = 0
    private val apiManager = ApiManager()
    private val au = AccessibilityUtil()
    private var isLogin = false
    private var aes = AES()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ticker.startRunning()
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    private fun initialStage() {
        Log.d("initialStage", "initialStage  Event")
        printAllFlags().let { Log.d("Flags", it) }
        ticker.startReAgain()
        if (!MainActivity().isAccessibilityServiceEnabled(this, this.javaClass)) {
            return;
        }
        val rootNode: AccessibilityNodeInfo? = au.getTopMostParentNode(rootInActiveWindow)
        if (rootNode != null) {
            if (au.findNodeByPackageName(rootNode, Config.packageName) == null) {
                if (appNotOpenCounter > 4) {
                    Log.d("App Status", "Not Found")
                    relaunchApp()
                    try {
                        Thread.sleep(4000)
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                    appNotOpenCounter = 0
                    return
                }
                appNotOpenCounter++
            } else {
                checkForSessionExpiry()
                au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
                apiManager.checkUpiStatus { isActive ->
                    if (isActive) {
                        switchToMpIn()
                        enterPin()
                        myAccounts()
                        arrowDown()
                        accountBalance()
                        readTransaction()
                    } else {
                        closeAndOpenApp()
                    }
                }
            }
            rootNode.recycle()
        }
    }




    private fun switchToMpIn() {
        val mainList = au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
        if (!mainList.contains("Enter MPIN")) {
            val mpInUsername = au.findNodeByText(rootInActiveWindow, "MPIN USERNAME", false, false)
            mpInUsername?.apply {
                performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }


    private fun enterPin() {
        if (isLogin) return
        val loginPin = Config.loginPin
        if (loginPin.isNotEmpty()) {
            val enterMPIN =
                au.findNodeByText(
                    au.getTopMostParentNode(rootInActiveWindow),
                    "Enter MPIN",
                    false,
                    false
                )
            enterMPIN?.apply {
                val mPinTextField = au.findNodeByClassName(
                    rootInActiveWindow, "android.widget.EditText"
                )
                mPinTextField?.apply {
                    performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    try {
                        Thread.sleep(2000)
                    } catch (e: InterruptedException) {
                        throw java.lang.RuntimeException(e)
                    }
                    for (c in loginPin.toCharArray()) {
                        for (json in au.fixedPinedPosition()) {
                            val pinValue = json["pin"] as String?
                            if (pinValue != null && json["x"] != null && json["y"] != null) {
                                if (pinValue == c.toString()) {
                                    val x = json["x"].toString().toInt()
                                    val y = json["y"].toString().toInt()
                                    try {
                                        Thread.sleep(2000)
                                    } catch (e: InterruptedException) {
                                        e.printStackTrace()
                                    }
                                    println("Clicked on X : $x PIN $pinValue")
                                    println("Clicked on Y : $y PIN $pinValue")
                                    performTap(x.toFloat(), y.toFloat(), 650)
                                    ticker.startReAgain();
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(2000)
                    } catch (e: InterruptedException) {
                        throw java.lang.RuntimeException(e)
                    }
                    isLogin = true;
                }
            }
        }
    }


    private fun myAccounts() {
        val myAccounts =
            au.findNodeByContentDescription(
                au.getTopMostParentNode(rootInActiveWindow),
                "icon My Accounts"
            )
        myAccounts?.apply {
            performAction(AccessibilityNodeInfo.ACTION_CLICK);
            ticker.startReAgain();
        }
    }

    private fun arrowDown() {
        val arrowDownR =
            au.findNodeByContentDescription(
                au.getTopMostParentNode(rootInActiveWindow),
                "arrow-down-r"
            )
        arrowDownR?.apply {
            performAction(AccessibilityNodeInfo.ACTION_CLICK);
            ticker.startReAgain();
        }
    }

    private fun accountBalance() {
        val accountDetails = au.findNodeByText(rootInActiveWindow, "Account Details", false, false)
        if (accountDetails == null) {
            val accountBalance =
                au.findNodeByText(rootInActiveWindow, "Account Balance", false, false)
            accountBalance?.apply {
                performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }


    private var scrollCounter = 0
    private fun scrollToGetMore() {
        if (au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
                .contains("Delink Account")
        ) {
            val scrollLayout =
                au.findNodeByClassName(rootInActiveWindow, "android.widget.ListView")
            if (scrollLayout != null && scrollCounter < 1) {
                Thread.sleep(2000)
                val scrollBounds = Rect()
                scrollLayout.getBoundsInScreen(scrollBounds)
                val startX = scrollBounds.centerX()
                val startY = scrollBounds.centerY()
                val scrollDistance = 250
                val endY = startY - scrollDistance
                val path = Path()
                path.moveTo(startX.toFloat(), startY.toFloat())
                path.lineTo(startX.toFloat(), endY.toFloat())
                val gestureBuilder = GestureDescription.Builder()
                gestureBuilder.addStroke(StrokeDescription(path, 0, 100))
                dispatchGesture(gestureBuilder.build(), null, null)
                scrollCounter++
            }
        }
    }

    private fun filterList(): MutableList<String> {
        val mainList = au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
        val mutableList = mutableListOf<String>()
        if (mainList.contains("Delink Account")) {
            val unfilteredList = mainList.filter { it.isNotEmpty() }
            val aNoIndex = unfilteredList.indexOf("Delink Account")
            if (aNoIndex != -1 && aNoIndex < unfilteredList.size - 2) {
                val separatedList =
                    unfilteredList.subList(aNoIndex, unfilteredList.size).toMutableList()
                val modifiedList = separatedList.subList(5, separatedList.size)
                println("modifiedList $modifiedList")
                mutableList.addAll(modifiedList)
            }
        }
        return mutableList

    }

    private fun readTransaction() {
        val output = JSONArray()
        val mainList = au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
        try {
            var balance = ""
            for (i in 0..mainList.size) {
                if (mainList[i].contains("Account Balance")) {
                    balance = mainList[i - 2].replace("₹","")
                    break
                }
            }
            if (mainList.contains("Delink Account")) {
                val filterList = filterList();
                for (i in filterList.indices step 3) {
                    val date = filterList[i]
                    val description = filterList[i + 1]
                    val crOrDrAmount = filterList[i + 2]
                    var amount = ""
                    if (crOrDrAmount.contains("CR")) {
                        amount = crOrDrAmount.replace("CR", "").replace("₹", "").trim();
                    }
                    if (crOrDrAmount.contains("DR")) {
                        amount =
                            "-${crOrDrAmount.replace("DR", "").replace("₹", "").trim()}"
                    }
                    val entry = JSONObject()
                    try {
                        entry.put("Amount", amount.replace(",", ""))
                        entry.put("RefNumber", extractUTRFromDesc(description))
                        entry.put("Description", extractUTRFromDesc(description))
                        entry.put("AccountBalance", balance)
                        entry.put("CreatedDate", date)
                        entry.put("BankName", Config.bankName + Config.bankLoginId)
                        entry.put("BankLoginId", Config.bankLoginId)
                        entry.put("UPIId", getUPIId(description))
                        output.put(entry)
                    } catch (e: JSONException) {
                        throw java.lang.RuntimeException(e)
                    }
                }
                Log.d("Final Json Output", output.toString());
                Log.d("Total length", output.length().toString());
                if (output.length() > 0) {
                    val result = JSONObject()
                    try {
                            result.put("Result", aes.encrypt(output.toString()))
                            apiManager.saveBankTransaction(result.toString());
                        val homeIcon =
                            au.findNodeByText(rootInActiveWindow, "home-icon", false, false)
                        homeIcon?.apply {
                            performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }

                    } catch (e: JSONException) {
                        throw java.lang.RuntimeException(e)
                    }
                }


            }
        } catch (ignored: Exception) {
        }
    }


    private val queryUPIStatus = Runnable {
        val intent = packageManager.getLaunchIntentForPackage(Config.packageName)
        intent?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
        }
    }
    private val inActive = Runnable {
        Toast.makeText(this, "PSBScrapper inactive", Toast.LENGTH_LONG).show();
    }

    private fun relaunchApp() {
        apiManager.queryUPIStatus(queryUPIStatus, inActive)
    }

    private fun formatDate(inputDateString: String): String {
        val inputFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val outputFormat = SimpleDateFormat("d/M/yyyy", Locale.ENGLISH)
        val date = inputFormat.parse(inputDateString)
        return outputFormat.format(date!!)
    }


    private fun checkForSessionExpiry() {

        val node1 =
            au.findNodeByText(
                rootInActiveWindow,
                "Your session has been timed out, please login again.",
                false,
                false
            )
        val node2 =
            au.findNodeByText(
                rootInActiveWindow,
                "There is some difficulty in processing the request. Please try again later.",
                false,
                false
            )

        val node3 = au.findNodeByText(
            rootInActiveWindow,
            "There is some difficulty in processing the request. Please try again later.",
            false,
            false
        )
        val node4 = au.findNodeByText(
            rootInActiveWindow,
            "Do You Want to Logout?",
            false,
            false
        )

        if (node4 != null) {
            val yesButton =
                au.findNodeByText(rootInActiveWindow, "Yes", false, false)
            yesButton?.apply {
                performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val intent = packageManager.getLaunchIntentForPackage(packageName.toString())
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    isLogin = false;
                } else {
                    Log.e("AccessibilityService", "App not found: " + packageName.toString())
                }
            }
        }


        if (node1 != null || node2 != null) {
            val extendMySession =
                au.findNodeByText(rootInActiveWindow, "OK", false, false)
            extendMySession?.apply {
                isLogin = false;
                performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
       val allText = au.listAllTextsInActiveWindow(rootInActiveWindow)
        if(allText.contains("My Accounts"))
        {
            if(allText.contains("Record not found"))
            {
                val homeIcon =
                    au.findNodeByText(rootInActiveWindow, "home-icon", false, false)
                homeIcon?.apply {
                    performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }

        }



        if (node3 != null) {
            val extendMySession =
                au.findNodeByText(rootInActiveWindow, "OK", false, false)
            extendMySession?.apply {
                val enterMPIN =
                    au.findNodeByText(
                        au.getTopMostParentNode(rootInActiveWindow),
                        "Enter MPIN",
                        false,
                        false
                    )
                enterMPIN?.apply {
                    closeAndOpenApp()
                    isLogin = false
                }
                performAction(AccessibilityNodeInfo.ACTION_CLICK)


//                val intent = packageManager.getLaunchIntentForPackage(packageName.toString())
//                if (intent != null) {
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                    startActivity(intent)
//                } else {
//                    Log.e("AccessibilityService", "App not found: " + packageName.toString())
//                }
            }
        }


    }

    private fun closeAndOpenApp() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }


    private fun performTap(x: Float, y: Float, duration: Long) {
        Log.d("Accessibility", "Tapping $x and $y")
        val p = Path()
        p.moveTo(x, y)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(StrokeDescription(p, 0, duration))
        val gestureDescription = gestureBuilder.build()
        var dispatchResult = false
        dispatchResult = dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
            }
        }, null)
        Log.d("Dispatch Result", dispatchResult.toString())
    }

    private fun getUPIId(description: String): String {
        if (!description.contains("@")) return ""
        val split: Array<String?> =
            description.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        var value: String? = null
        value = Arrays.stream(split).filter { x: String? ->
            x!!.contains(
                "@"
            )
        }.findFirst().orElse(null)
        return value ?: ""

    }

    private fun extractUTRFromDesc(description: String): String? {
        return try {
            val split: Array<String?> =
                description.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            var value: String? = null
            value = Arrays.stream(split).filter { x: String? -> x!!.length == 12 }
                .findFirst().orElse(null)
            if (value != null) {
                "$value $description"
            } else description
        } catch (e: Exception) {
            description
        }
    }


    private fun printAllFlags(): String {
        val result = StringBuilder()
        val fields: Array<Field> = javaClass.declaredFields
        for (field in fields) {
            field.isAccessible = true
            val fieldName: String = field.name
            try {
                val value: Any? = field.get(this)
                result.append(fieldName).append(": ").append(value).append("\n")
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        return result.toString()
    }

}