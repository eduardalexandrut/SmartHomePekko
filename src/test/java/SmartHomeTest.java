import org.apache.pekko.actor.*;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestKit;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.util.JavaDurationConverters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SmartHomeTest {

    private ActorSystem system;

    // Fast delays for testing
    private final java.time.Duration fastExitDelay = java.time.Duration.ofMillis(100);
    private final java.time.Duration fastEntryDelay = java.time.Duration.ofMillis(100);

    @BeforeEach
    public void setup() {
        system = ActorSystem.create("TestAlarmSystem");
    }

    @AfterEach
    public void tearDown() {
        scala.concurrent.duration.FiniteDuration duration =
                scala.concurrent.duration.Duration.create(2, TimeUnit.SECONDS);
        TestKit.shutdownActorSystem(system, duration, true);
    }

    @Test
    public void testSirenFiresOnTimeout() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);

        // Home layout
        Map<String, String> testConfig = Map.of(
                "LivingRoomMotion", "GroundFloor",
                "FrontDoor", "Perimeter",
                "BedroomMotion", "UpperFloor"
        );

        final ActorRef controlUnit = system.actorOf(ControlUnit.props(sirenProbe.ref(), fastExitDelay, fastEntryDelay, testConfig));
        final ActorRef motionSensor = system.actorOf(Sensor.props("LivingRoomMotion", controlUnit));

        // Arm the system
        Set<String> zonesToArm = Set.of("GroundFloor", "Perimeter");
        controlUnit.tell(new SmartHomeProtocol.ArmSystemRequest(zonesToArm), kit.testActor());

        // Wait out the fast exit delay (150ms covers 100ms)
        try { Thread.sleep(150); } catch (InterruptedException e) {}

        // Trigger the sensor
        motionSensor.tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        // Assert the Siren fires after the fast entry delay expires
        FiniteDuration assertionTimeout = JavaDurationConverters.asFiniteDuration(java.time.Duration.ofMillis(200));
        sirenProbe.expectMsgClass(assertionTimeout, SmartHomeProtocol.ActivateSiren.class);
    }

    @Test
    public void testSuccessfulDisarmDuringEntryDelay() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);

        // Define the layout
        Map<String, String> testConfig = Map.of(
                "LivingRoomMotion", "GroundFloor",
                "FrontDoor", "Perimeter",
                "BedroomMotion", "UpperFloor"
        );

        final ActorRef controlUnit = system.actorOf(ControlUnit.props(sirenProbe.ref(), fastExitDelay, fastEntryDelay, testConfig));
        final ActorRef frontDoorSensor = system.actorOf(Sensor.props("FrontDoor", controlUnit));
        final ActorRef keypad = system.actorOf(KeyPad.props(controlUnit));

        // Arm all zones
        Set<String> zonesToArm = Set.of("GroundFloor", "Perimeter", "UpperFloor");
        controlUnit.tell(new SmartHomeProtocol.ArmSystemRequest(zonesToArm), kit.testActor());

        try { Thread.sleep(150); } catch (InterruptedException e) {}

        // Simulate intrusion
        frontDoorSensor.tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        // Allow time for ControlUnit to process the intrusion and enter entryDelayState
        try { Thread.sleep(20); } catch (InterruptedException e) {}

        keypad.tell(new SmartHomeProtocol.InsertPinMsg("1111"), kit.testActor());

        // Assert that the Siren NEVER received an ActivateSiren command
        FiniteDuration safetyWindow = scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS);
        sirenProbe.expectNoMessage(safetyWindow);
    }

    @Test
    public void testPartialArmingIgnoresInactiveZones() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);

        // Define layout
        Map<String, String> testConfig = Map.of(
                "LivingRoomMotion", "GroundFloor",
                "FrontDoor", "Perimeter",
                "BedroomMotion", "UpperFloor"
        );


        final ActorRef controlUnit = system.actorOf(ControlUnit.props(sirenProbe.ref(), fastExitDelay, fastEntryDelay, testConfig));
        final ActorRef bedroomSensor = system.actorOf(Sensor.props("BedroomMotion", controlUnit));

        // Night Mode: Arm ONLY the Perimeter and GroundFloor. Leave "UpperFloor" inactive!
        Set<String> nightModeZones = Set.of("Perimeter", "GroundFloor");
        controlUnit.tell(new SmartHomeProtocol.ArmSystemRequest(nightModeZones), kit.testActor());

        try { Thread.sleep(150); } catch (InterruptedException e) {}

        // Trigger user movement upstairs in the inactive zone
        bedroomSensor.tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        // Assert that the control unit completely ignores it and the siren never goes off
        FiniteDuration safetyWindow = scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS);
        sirenProbe.expectNoMessage(safetyWindow);
    }
}
