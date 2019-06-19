package io.paleocrafter.booksearch.auth

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.SessionAuthChallenge
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.session
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.sessions.Sessions
import io.ktor.sessions.clear
import io.ktor.sessions.cookie
import io.ktor.sessions.directorySessionStorage
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.util.hex
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun Application.auth() {
    transaction {
        SchemaUtils.createMissingTablesAndColumns(AppUsers)
    }

    val keyString = environment.config.propertyOrNull("crypto.key")?.getString()
        ?: throw IllegalStateException("'crypto.key' must be configured! Either specify it in config file or pass CRYPTO_KEY env variable.")
    val hashKey = SecretKeySpec(hex(keyString), "HmacSHA1")

    fun hash(password: String): String {
        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(hashKey)
        return hex(hmac.doFinal(password.toByteArray(Charsets.UTF_8)))
    }

    transaction {
        if (User.all().none()) {
            val defaultPassword = environment.config.propertyOrNull("auth.admin-password")?.getString()
                ?: throw IllegalStateException("There are no users yet and no default admin password is set! " +
                    "Either specify it as 'auth.admin-password' in config file or pass DEFAULT_PASSWORD env variable.")
            AppUsers.insert {
                it[username] = "admin"
                it[password] = hash(defaultPassword)
                it[canManageBooks] = true
                it[canManageUsers] = true
            }

            environment.log.info("Superuser 'admin' was created with the provided default password!")
        }
    }

    install(Sessions) {
        cookie<UserId>("AUTH_SESSION", directorySessionStorage(File(".sessions"), cached = false))
    }

    install(Authentication) {
        session<UserId> {
            challenge = SessionAuthChallenge.Unauthorized
            validate {
                it
            }
        }
    }

    routing {
        route("/api/auth") {
            post("/login") {
                if (call.sessions.get<UserId>() != null) {
                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "message" to "You're already logged in!"
                        )
                    )
                }

                val request = call.receive<LoginRequest>()

                val (user, hasLoggedIn) = transaction {
                    val dbUser = User.find { (AppUsers.username eq request.username) and (AppUsers.password eq hash(request.password)) }
                        .firstOrNull() ?: return@transaction null

                    dbUser.view to dbUser.hasLoggedIn
                } ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "message" to "Unknown username/password combination!"
                    )
                )

                call.sessions.set(UserId(user.id))

                if (!hasLoggedIn) {
                    transaction {
                        User.findById(user.id)?.hasLoggedIn = true
                    }
                }

                call.respond(
                    mapOf(
                        "message" to "You were successully logged in!",
                        "user" to user,
                        "firstLogin" to !hasLoggedIn
                    )
                )
            }

            authenticate {
                post("/logout") {
                    call.sessions.clear<UserId>()

                    call.respond(
                        mapOf("message" to "You've been successfully logged out!")
                    )
                }

                get("/identity") {
                    val user = call.user ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "message" to "You need to be logged in to view this page!"
                        )
                    )

                    call.respond(user)
                }
            }

            authorize({ it.canManageUsers }) {
                route("/users") {
                    get("/") {
                        call.respond(transaction { User.all().map { it.view } })
                    }

                    patch("/{id}") {
                        val id = UUID.fromString(call.parameters["id"])
                        val request = call.receive<PatchUserRequest>()

                        val userName = transaction {
                            val user = User.findById(id) ?: return@transaction null

                            user.canManageBooks = request.canManageBooks
                            user.canManageUsers = request.canManageUsers

                            user.username
                        } ?: return@patch call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("message" to "User with ID '$id' does not exist!")
                        )

                        call.respond(
                            mapOf("message" to "Permissions for user '$userName' were successfully updated!")
                        )
                    }

                    delete("/{id}") {
                        val id = UUID.fromString(call.parameters["id"])
                        val userName = transaction {
                            val user = User.findById(id) ?: return@transaction null
                            val name = user.username

                            user.delete()

                            name
                        } ?: return@delete call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("message" to "User with ID '$id' does not exist!")
                        )

                        call.respond(
                            mapOf("message" to "User '$userName' was successfully deleted!")
                        )
                    }

                    put("/") {
                        val request = call.receive<CreateUserRequest>()

                        if (request.username.isEmpty() || request.password.isEmpty()) {
                            return@put call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("message" to "Username and password must not be empty!")
                            )
                        }

                        if (transaction { User.find { AppUsers.username eq request.username }.any() }) {
                            return@put call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("message" to "User with name '${request.username}' already exists!")
                            )
                        }

                        val user = transaction {
                            val userId = UUID.randomUUID()

                            AppUsers.insert {
                                it[id] = EntityID(userId, this)
                                it[username] = request.username
                                it[password] = hash(request.password)
                                it[canManageBooks] = request.canManageBooks
                                it[canManageUsers] = request.canManageUsers
                            }

                            User.findById(userId)?.view
                        }

                        call.respond(
                            mapOf(
                                "message" to "User '${request.username}' was successfully created!",
                                "user" to user
                            )
                        )
                    }
                }
            }
        }
    }
}

val ApplicationCall.userId: UUID?
    get() = authentication.principal<UserId>()?.id

val ApplicationCall.user: UserView?
    get() = transaction { User.findById(userId ?: return@transaction null)?.view }

fun Route.authorize(check: (UserView) -> Boolean, build: Route.() -> Unit): Route {
    return authenticate {
        intercept(ApplicationCallPipeline.Call) {
            val user = call.user
            if (user != null && !check(user)) {
                call.respond(HttpStatusCode.Forbidden)
                return@intercept finish()
            }
        }

        build()
    }
}

data class LoginRequest(val username: String, val password: String)

data class CreateUserRequest(val username: String, val password: String, val canManageBooks: Boolean, val canManageUsers: Boolean)

data class PatchUserRequest(val canManageBooks: Boolean, val canManageUsers: Boolean)

data class UserId(val id: UUID) : Principal