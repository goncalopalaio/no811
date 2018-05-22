package gplio.com.no811

import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)


        val bitmap = BitmapFactory.decodeStream(
                assets.open("dog.jpg"))

        val classifier = InceptionClassifier(assets)
        for (recognition in classifier.recognize(bitmap)) {
            Log.d("ZZZ", "$recognition")
        }

        val bitmap2 = BitmapFactory.decodeStream(
                assets.open("dog.jpg"))

        for (recognition in classifier.recognize(bitmap2)) {
            Log.d("ZZZ", "2 $recognition")
        }


        classifier.close()
    }
}
