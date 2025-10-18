package org.monfluo.wallet.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class UriPaymentTest {

    @ParameterizedTest
    @MethodSource("paymentUris")
    fun `should correctly parse payment uri`(paymentUriRaw: String, expected: UriPayment?) {
        assertEquals(expected, UriPayment.parse(paymentUriRaw))
    }

    companion object {

        @JvmStatic
        fun paymentUris(): List<Arguments> = listOf(
            Arguments.of(
                "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                UriPayment(
                    listOf(
                        "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ"
                    ),
                    emptyList(),
                    emptyList(),
                    null
                )
            ),
            Arguments.of(
                "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                UriPayment(
                    listOf(
                        "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ"
                    ),
                    emptyList(),
                    emptyList(),
                    null
                )
            ),
            Arguments.of(
                "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?tx_amount=123.456789123456",
                UriPayment(
                    listOf(
                        "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ"
                    ),
                    listOf(
                        "123.456789123456"
                    ),
                    emptyList(),
                    null
                )
            ),
            Arguments.of(
                "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?amount=123.456789123456",
                UriPayment(
                    listOf(
                        "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ"
                    ),
                    listOf(
                        "123.456789123456"
                    ),
                    emptyList(),
                    null
                )
            ),
            Arguments.of(
                "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ;84DzXYC9YQQhb2eviDbkRoNZYe1VQmidQMZFvWnyBz8HQhHAngPK7C2PzmWsVveeWWcXCvpEEdsz9fXeFA8bByy38uhSy7n?tx_amount=123.456789123456;0.1",
                UriPayment(
                    listOf(
                        "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        "84DzXYC9YQQhb2eviDbkRoNZYe1VQmidQMZFvWnyBz8HQhHAngPK7C2PzmWsVveeWWcXCvpEEdsz9fXeFA8bByy38uhSy7n"
                    ),
                    listOf(
                        "123.456789123456",
                        "0.1"
                    ),
                    emptyList(),
                    null
                )
            ),
            Arguments.of(
                "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ;84DzXYC9YQQhb2eviDbkRoNZYe1VQmidQMZFvWnyBz8HQhHAngPK7C2PzmWsVveeWWcXCvpEEdsz9fXeFA8bByy38uhSy7n?tx_amount=123.456789123456;0.1&recipient_name=Monfluo%201;Monfluo%202",
                UriPayment(
                    listOf(
                        "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        "84DzXYC9YQQhb2eviDbkRoNZYe1VQmidQMZFvWnyBz8HQhHAngPK7C2PzmWsVveeWWcXCvpEEdsz9fXeFA8bByy38uhSy7n"
                    ),
                    listOf(
                        "123.456789123456",
                        "0.1"
                    ),
                    listOf(
                        "Monfluo 1",
                        "Monfluo 2",
                    ),
                    null
                )
            ),
            Arguments.of(
                "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?tx_description=Donation%20to%20Monfluo",
                UriPayment(
                    listOf(
                        "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ"
                    ),
                    emptyList(),
                    emptyList(),
                    "Donation to Monfluo"
                )
            ),
            Arguments.of(
                "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?description=Donation%20to%20Monfluo",
                UriPayment(
                    listOf(
                        "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ"
                    ),
                    emptyList(),
                    emptyList(),
                    "Donation to Monfluo"
                )
            ),
            Arguments.of(
                "notmonero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                null
            ),
        )
    }
}
