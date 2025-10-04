package com.enzium.smarttrack

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

@QuarkusTest
class ExpenseResourceTest {

    @Test
    fun testHelloEndpoint() {
        given()
          .`when`().get("/expenses")
          .then()
             .statusCode(200)
             .body(`is`("Hello from Quarkus REST"))
    }

}