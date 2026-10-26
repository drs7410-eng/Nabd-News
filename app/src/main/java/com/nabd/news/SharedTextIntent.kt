package com.nabd.news

import android.content.Intent

/** Consume explicit shared text once, without replacing the Activity's launch intent or reading URIs. */
internal fun consumeSharedText(intent:Intent?):String? {
    if(intent?.action!=Intent.ACTION_SEND || intent.type!="text/plain")return null
    val text=runCatching {
        (intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?:intent.clipData?.takeIf{it.itemCount>0}?.getItemAt(0)?.text)?.toString()?.take(20000)
    }.getOrNull()
    runCatching{intent.removeExtra(Intent.EXTRA_TEXT);intent.clipData=null}
    return text?.takeIf{it.isNotBlank()}
}
