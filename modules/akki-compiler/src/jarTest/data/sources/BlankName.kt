package sample

import io.akki.Log
import io.akki.Logger

private const val BLANK = " "

fun blankName(): Logger = Log.named(BLANK)
