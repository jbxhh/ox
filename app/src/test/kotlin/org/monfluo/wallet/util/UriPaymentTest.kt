package org.monfluo.wallet.android.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class UriPaymentTest {
    @ParameterizedTest
    @MethodSource("paymentUriStrings")
    fun `should parse payment uri`(
        paymentUriRaw: String,
        expected: UriPayment?,
    ) {
        assertEquals(expected, UriPayment.parse(paymentUriRaw))
    }

    @ParameterizedTest
    @MethodSource("paymentUris")
    fun `should convert payment uri into string`(
        paymentUri: UriPayment,
        expected: String,
    ) {
        assertEquals(expected, paymentUri.toUriString())
        assertEquals(paymentUri, UriPayment.parse(paymentUri.toUriString()))
    }

    companion object {
        @JvmStatic
        fun paymentUriStrings(): List<Arguments> =
            listOf(
                Arguments.of(
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        emptyList(),
                        emptyList(),
                        null,
                    ),
                ),
                Arguments.of(
                    "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        emptyList(),
                        emptyList(),
                        null,
                    ),
                ),
                Arguments.of(
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?tx_amount=123.456789123456",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        listOf(
                            "123.456789123456",
                        ),
                        emptyList(),
                        null,
                    ),
                ),
                Arguments.of(
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?amount=123.456789123456",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        listOf(
                            "123.456789123456",
                        ),
                        emptyList(),
                        null,
                    ),
                ),
                Arguments.of(
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ;84DzXYC9YQQhb2eviDbkRoNZYe1VQmidQMZFvWnyBz8HQhHAngPK7C2PzmWsVveeWWcXCvpEEdsz9fXeFA8bByy38uhSy7n?tx_amount=123.456789123456;0.1",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                            "84DzXYC9YQQhb2eviDbkRoNZYe1VQmidQMZFvWnyBz8HQhHAngPK7C2PzmWsVveeWWcXCvpEEdsz9fXeFA8bByy38uhSy7n",
                        ),
                        listOf(
                            "123.456789123456",
                            "0.1",
                        ),
                        emptyList(),
                        null,
                    ),
                ),
                Arguments.of(
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ;84DzXYC9YQQhb2eviDbkRoNZYe1VQmidQMZFvWnyBz8HQhHAngPK7C2PzmWsVveeWWcXCvpEEdsz9fXeFA8bByy38uhSy7n?tx_amount=123.456789123456;0.1&recipient_name=Monfluo%201;Monfluo%202",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                            "84DzXYC9YQQhb2eviDbkRoNZYe1VQmidQMZFvWnyBz8HQhHAngPK7C2PzmWsVveeWWcXCvpEEdsz9fXeFA8bByy38uhSy7n",
                        ),
                        listOf(
                            "123.456789123456",
                            "0.1",
                        ),
                        listOf(
                            "Monfluo 1",
                            "Monfluo 2",
                        ),
                        null,
                    ),
                ),
                Arguments.of(
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?tx_description=Donation%20to%20Monfluo",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        emptyList(),
                        emptyList(),
                        "Donation to Monfluo",
                    ),
                ),
                Arguments.of(
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?description=Donation%20to%20Monfluo",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        emptyList(),
                        emptyList(),
                        "Donation to Monfluo",
                    ),
                ),
                Arguments.of(
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?message=%2410+dollars",
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        emptyList(),
                        emptyList(),
                        "$10 dollars",
                    ),
                ),
                Arguments.of(
                    "notmonero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                    null,
                ),
            )

        @JvmStatic
        fun paymentUris(): List<Arguments> =
            listOf(
                Arguments.of(
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        emptyList(),
                        emptyList(),
                        null,
                    ),
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                ),
                Arguments.of(
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        listOf("0.123456"),
                        emptyList(),
                        null,
                    ),
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?tx_amount=0.123456",
                ),
                Arguments.of(
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        emptyList(),
                        listOf("Me, Myself & I"),
                        null,
                    ),
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?recipient_name=Me%2C+Myself+%26+I",
                ),
                Arguments.of(
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        emptyList(),
                        emptyList(),
                        "Donation to Monfluo",
                    ),
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?tx_description=Donation+to+Monfluo",
                ),
                Arguments.of(
                    UriPayment(
                        listOf(
                            "48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ",
                        ),
                        listOf("0.123456"),
                        listOf("Me, Myself & I"),
                        "Donation to Monfluo",
                    ),
                    "monero:48KqWkaWd8KcJKkakWfknJEo16scc8YkycfKpb8JYchiKJfukY1ompX9eQ9n3CfEBU1ADGj3P5FLAb731J2UghQt142LwHQ?tx_amount=0.123456&recipient_name=Me%2C+Myself+%26+I&tx_description=Donation+to+Monfluo",
                ),
            )
    }
}
