package com.yongchul.booking

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AccommodationBookingApplication

fun main(args: Array<String>) {
    runApplication<AccommodationBookingApplication>(*args)
}
