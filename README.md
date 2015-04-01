Bank-account-data-storage
==========================

A little application that reads from a queue bank account credentials and saves them.
It also fetches transactions through HBCI.

The configuration in default.props.template:
For connection to the message queue use the default settings of RabbitMQ.
connection.user=guest
connection.password=guest
