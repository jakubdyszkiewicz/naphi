package org.naphi.server.error

import org.naphi.contract.Status
import java.lang.RuntimeException

open class StatusException(status: Status): RuntimeException(status.reason)
class NotFoundException : StatusException(Status.NOT_FOUND)
