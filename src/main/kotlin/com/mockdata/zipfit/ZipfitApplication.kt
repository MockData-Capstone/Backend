package com.mockdata.zipfit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ZipfitApplication

fun main(args: Array<String>) {
	runApplication<ZipfitApplication>(*args)
}
