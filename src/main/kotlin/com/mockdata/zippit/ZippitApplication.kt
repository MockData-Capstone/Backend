package com.mockdata.zippit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ZippitApplication

fun main(args: Array<String>) {
	runApplication<ZippitApplication>(*args)
}
