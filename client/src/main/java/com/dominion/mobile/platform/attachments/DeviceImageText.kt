package com.dominion.mobile.platform.attachments

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device OCR of a user-selected image. No bytes go to the model gateway.
 * It is text recognition, NOT semantic image understanding. */
internal object DeviceImageText {
    private suspend fun scan(recognizer:TextRecognizer,image:InputImage):String =
        suspendCancellableCoroutine{continuation->
            recognizer.process(image)
                .addOnSuccessListener{result->
                    if(continuation.isActive) continuation.resume(result.text.trim())
                }
                .addOnFailureListener{failure->
                    if(continuation.isActive) continuation.resumeWithException(failure)
                }
        }

    suspend fun recognize(bytes:ByteArray):String=withContext(Dispatchers.Default){
        require(bytes.isNotEmpty()&&bytes.size<=LocalAttachment.MAX_BYTES)
        val dimensions=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,dimensions)
        require(dimensions.outWidth in 1..12000&&dimensions.outHeight in 1..12000)
        val options=BitmapFactory.Options().apply{
            var scale=1
            while(dimensions.outWidth/scale>2400||dimensions.outHeight/scale>2400)
                scale*=2
            inSampleSize=scale
        }
        val bitmap=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options))
        try{
            val image=InputImage.fromBitmap(bitmap,0)
            withTimeout(25000L){
                val korean=TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                try{
                    val primary=try{scan(korean,image)}
                        catch(cancelled:CancellationException){throw cancelled}
                        catch(_:Exception){""}
                    if(primary.length>=3)return@withTimeout primary.take(7000)
                }finally{korean.close()}
                val latin=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try{scan(latin,image).take(7000)}finally{latin.close()}
            }
        }finally{bitmap.recycle()}
    }
}
