/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package device

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import device.DeviceManager._

class DeviceManagerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "DeviceManager actor" must {

    "reply to registration requests" in {
      val probe = createTestProbe[DeviceRegistered]()
      val managerActor = spawn(DeviceManager())

      managerActor ! RequestTrackDevice("group1", "device", probe.ref)
      val registered1 = probe.receiveMessage()

      managerActor ! RequestTrackDevice("group2", "device", probe.ref)
      val registered2 = probe.receiveMessage()

      registered1.device should !==(registered2.device)
    }

  }

}
