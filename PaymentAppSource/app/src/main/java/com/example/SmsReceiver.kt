package com.example
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                try {
                    val pdus = bundle.get("pdus") as Array<*>
                    for (pdu in pdus) {
                        val format = bundle.getString("format")
                        val message = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        val sender = message.displayOriginatingAddress ?: continue
                        val body = message.displayMessageBody ?: continue
                        if (isValidSender(sender)) {
                            parseAndSaveTransaction(sender, body)
                        }
                    }
                } catch (e: Exception) { Log.e("Sms", "Error", e) }
            }
        }
    }

    private fun isValidSender(sender: String): Boolean {
        val s = sender.lowercase()
        return s.contains("bkash") || s.contains("nagad") || s == "16216" || s == "16167"
    }

    private fun parseAndSaveTransaction(sender: String, message: String) {
        var amount: Double? = null
        var trxId: String? = null
        val amountMatcher = Pattern.compile("Tk\\s?([\\d,.]+)").matcher(message)
        if (amountMatcher.find()) amount = amountMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        
        val trxMatcher = Pattern.compile("T[rx]xn?I[dD]\\s?([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(message)
        if (trxMatcher.find()) trxId = trxMatcher.group(1)
        
        if (amount != null && trxId != null) {
            val db = FirebaseFirestore.getInstance()
            val data = hashMapOf("senderName" to sender, "amount" to amount, "trxId" to trxId, "timestamp" to System.currentTimeMillis())
            db.collection("payment_transactions").document(trxId).set(data)
        }
    }
}