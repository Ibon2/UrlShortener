package auxiliar

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat

class Graphics {
    private val listData = ArrayList<Double>()
    private val listLabel = ArrayList<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss")
    @OptIn(DelicateCoroutinesApi::class)
    fun save(timeData: Double, timeTime: Double) = runBlocking {
        GlobalScope.launch {
            if (listData.size < 10 && listLabel.size < 10) {
                listData.add(timeData)
                listLabel.add(dateFormat.format(timeTime))
            } else {
                listData.removeFirst()
                listLabel.removeFirst()
                listData.add(timeData)
                listLabel.add(dateFormat.format(timeTime))
            }
        }
    }
    fun getListData(): ArrayList<Double>{
        return listData
    }
    fun getListLabel(): ArrayList<String>{
        return listLabel
    }
}