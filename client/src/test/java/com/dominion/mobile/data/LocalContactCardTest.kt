package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test
import com.google.zxing.*
import com.google.zxing.qrcode.*
import com.google.zxing.common.HybridBinarizer
class LocalContactCardTest {
    @Test fun actualQrRoundTrip() {
        val value = LocalContactCard("테스트", "+82 10-1234-5678", "test@example.com")
        val matrix = QRCodeWriter().encode(value.encode(), BarcodeFormat.QR_CODE, 512, 512)
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width,i / matrix.width]) 0xff000000.toInt() else -1 }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width,matrix.height,pixels))))
        assertEquals(value.validated(), LocalContactCard.decode(decoded.text))
    }
    @Test fun rejectsUnrelatedLinksAndInjectedTargets() {
        assertTrue(runCatching { LocalContactCard.decode("https://example.com/") }.isFailure)
        assertTrue(runCatching { LocalContactCard("name","123;456").encode() }.isFailure)
        assertTrue(runCatching { LocalContactCard("name",email="a@example.com?bcc=b@example.com").encode() }.isFailure)
    }
}
