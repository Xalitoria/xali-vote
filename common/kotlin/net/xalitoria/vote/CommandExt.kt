package net.xalitoria.vote

fun String.withPlayer(name: String): String = replace("{player}", name)
