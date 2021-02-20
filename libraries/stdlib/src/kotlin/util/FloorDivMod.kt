/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Auto-generated file. DO NOT EDIT!

@file:kotlin.jvm.JvmName("NumbersKt")
@file:kotlin.jvm.JvmMultifileClass
package kotlin

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Byte.floorDiv(other: Byte): Int = 
    this.toInt().floorDiv(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Byte.mod(other: Byte): Int = 
    this.toInt().mod(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Byte.floorDiv(other: Short): Int = 
    this.toInt().floorDiv(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Byte.mod(other: Short): Int = 
    this.toInt().mod(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Byte.floorDiv(other: Int): Int = 
    this.toInt().floorDiv(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Byte.mod(other: Int): Int = 
    this.toInt().mod(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Byte.floorDiv(other: Long): Long = 
    this.toLong().floorDiv(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Byte.mod(other: Long): Long = 
    this.toLong().mod(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Short.floorDiv(other: Byte): Int = 
    this.toInt().floorDiv(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Short.mod(other: Byte): Int = 
    this.toInt().mod(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Short.floorDiv(other: Short): Int = 
    this.toInt().floorDiv(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Short.mod(other: Short): Int = 
    this.toInt().mod(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Short.floorDiv(other: Int): Int = 
    this.toInt().floorDiv(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Short.mod(other: Int): Int = 
    this.toInt().mod(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Short.floorDiv(other: Long): Long = 
    this.toLong().floorDiv(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Short.mod(other: Long): Long = 
    this.toLong().mod(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Int.floorDiv(other: Byte): Int = 
    this.floorDiv(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Int.mod(other: Byte): Int = 
    this.mod(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Int.floorDiv(other: Short): Int = 
    this.floorDiv(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Int.mod(other: Short): Int = 
    this.mod(other.toInt())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Int.floorDiv(other: Int): Int {
    var q = this / other
    if (this xor other < 0 && q * other != this) q-- 
    return q
}

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Int.mod(other: Int): Int {
    val r = this % other
    return r + (other and (((r xor other) and (r or -r)) shr 31))
}

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Int.floorDiv(other: Long): Long = 
    this.toLong().floorDiv(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Int.mod(other: Long): Long = 
    this.toLong().mod(other)

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Long.floorDiv(other: Byte): Long = 
    this.floorDiv(other.toLong())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Long.mod(other: Byte): Long = 
    this.mod(other.toLong())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Long.floorDiv(other: Short): Long = 
    this.floorDiv(other.toLong())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Long.mod(other: Short): Long = 
    this.mod(other.toLong())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Long.floorDiv(other: Int): Long = 
    this.floorDiv(other.toLong())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Long.mod(other: Int): Long = 
    this.mod(other.toLong())

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Long.floorDiv(other: Long): Long {
    var q = this / other
    if (this xor other < 0 && q * other != this) q-- 
    return q
}

@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
public inline fun Long.mod(other: Long): Long {
    val r = this % other
    return r + (other and (((r xor other) and (r or -r)) shr 63))
}

