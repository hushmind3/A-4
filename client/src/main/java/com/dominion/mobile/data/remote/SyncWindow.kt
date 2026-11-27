package com.dominion.mobile.data.remote

internal data class SyncWindow(val rooms:List<String>,val nextCursor:Int) {
    companion object {
        /** A busy room gets priority, but never starves the round-robin history scan. */
        fun choose(ids:List<String>,priority:Set<String>,cursor:Int,limit:Int=8):SyncWindow {
            require(limit>=2)
            val all=ids.filter{it.isNotBlank()}.distinct().sorted()
            if(all.isEmpty())return SyncWindow(emptyList(),0)
            val urgent=all.filter{it in priority}.take(limit/2)
            val start=Math.floorMod(cursor,all.size)
            val rotation=(all.drop(start)+all.take(start))
            val scan=rotation.filterNot{it in urgent}.take(limit-urgent.size)
            return SyncWindow(urgent+scan,(start+maxOf(1,scan.size))%all.size)
        }
    }
}
