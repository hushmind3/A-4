package com.dominion.mobile.platform.automation

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.dominion.mobile.data.PhonePublicResult
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only an exact, unique, safe navigation label can become a visual target.
 * Image data and arbitrary text never leave the handset. */
internal object ScreenTextNavigation{
    val allowed=setOf("검색","Search","통합 검색","장소 검색","상품 검색","음식점 검색","숙소 검색",
        "병원 검색","길찾기","검색 결과","지도","홈","Home","대중교통","도보","자동차","고객센터","도움말")
    data class Target(val label:String,val x:Int,val y:Int)
    suspend fun targets(png:ByteArray):List<Target> = withContext(Dispatchers.Default){
        require(png.size in 1..5*1024*1024)
        val bitmap=requireNotNull(BitmapFactory.decodeByteArray(png,0,png.size))
        try{
            require(bitmap.width in 1..4096&&bitmap.height in 1..4096)
            val recognizer=TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            try{
                val recognized=withTimeout(10000L){
                    suspendCancellableCoroutine<com.google.mlkit.vision.text.Text>{continuation->
                        recognizer.process(InputImage.fromBitmap(bitmap,0))
                            .addOnSuccessListener{if(continuation.isActive)continuation.resume(it)}
                            .addOnFailureListener{if(continuation.isActive)continuation.resumeWithException(it)}
                    }
                }
                val lines=recognized.textBlocks.flatMap{it.lines}
                if(PhonePublicResult.sensitive(lines.map{it.text}))return@withContext emptyList()
                lines.mapNotNull{line->
                    val label=line.text.trim()
                    val rect=line.boundingBox?:return@mapNotNull null
                    if(label !in allowed||!PhonePublicResult.safeLabel(label))return@mapNotNull null
                    val x=rect.centerX()*1000/bitmap.width
                    val y=rect.centerY()*1000/bitmap.height
                    if(x !in 25..975||y !in 25..975)return@mapNotNull null
                    Target(label,x,y)
                }.groupBy{it.label}.values.mapNotNull{it.singleOrNull()}
            }finally{recognizer.close()}
        }finally{bitmap.recycle()}
    }
}
