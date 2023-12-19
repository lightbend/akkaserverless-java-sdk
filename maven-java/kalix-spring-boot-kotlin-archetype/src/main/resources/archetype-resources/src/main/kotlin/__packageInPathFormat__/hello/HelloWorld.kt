package ${package}.hello;

import kalix.javasdk.action.Action
import kalix.javasdk.annotations.Acl
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * This is a simple Action that returns "Hello World!".
 * Locally, you can access it by running `curl http://localhost:9000/hello`.
 */
@RequestMapping
// Exposing action to the Internet.
@Acl(allow = [Acl.Matcher(principal = Acl.Principal.INTERNET)])
class HelloWorld : Action() {

    @GetMapping("/hello")
    fun hello(): Effect<String> {
        return effects().reply("Hello World!")
    }
}