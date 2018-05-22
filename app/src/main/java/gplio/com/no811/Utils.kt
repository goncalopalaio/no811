package gplio.com.no811

import android.content.res.AssetManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Created by goncalo on 09-11-2017.
 */

fun readFileLinesFromAssets(assetManager: AssetManager, fileName: String): ArrayList<String> {
    val lines = ArrayList<String>()
    val br = BufferedReader(InputStreamReader(assetManager.open(fileName)))
    while (true) {
        val line = br.readLine()
        if (line == null || line.isEmpty()) {
            break
        }
        lines.add(line)
    }
    br.close()
    return lines
}