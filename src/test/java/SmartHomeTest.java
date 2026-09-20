import jdk.jfr.Description;
import org.apache.pekko.actor.*;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestKit;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.util.JavaDurationConverters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SmartHomeTest {

    private ActorSystem system;

    // Fast delays for rapid testing
    private static final Duration FAST_EXIT_DELAY = Duration.ofMillis(100);
    private static final Duration FAST_ENTRY_DELAY = Duration.ofMillis(100);

    // Shared home layout configuration
    private static final Map<String, String> TEST_CONFIG = Map.of(
            "LivingRoomMotion", "GroundFloor",
            "FrontDoor", "Perimeter",
            "BedroomMotion", "UpperFloor"
    );

    @BeforeEach
    public void setup() {
        system = ActorSystem.create("TestAlarmSystem");
    }

    @AfterEach
    public void tearDown() {
        FiniteDuration duration = scala.concurrent.duration.Duration.create(2, TimeUnit.SECONDS);
        TestKit.shutdownActorSystem(system, duration, true);
    }

    /**
     * Helper record to package standard test actors together and reduce duplication.
     */
    private record TestEnvironment(
            ActorRef controlUnit,
            ActorRef frontDoorSensor,
            ActorRef livingRoomSensor,
            ActorRef bedroomSensor,
            ActorRef keypad
    ) {}

    private TestEnvironment createEnvironment(TestProbe sirenProbe) {
        ActorRef controlUnit = system.actorOf(ControlUnit.props(sirenProbe.ref(), FAST_EXIT_DELAY, FAST_ENTRY_DELAY, TEST_CONFIG));
        ActorRef frontDoor = system.actorOf(Sensor.props("FrontDoor", controlUnit));
        ActorRef livingRoom = system.actorOf(Sensor.props("LivingRoomMotion", controlUnit));
        ActorRef bedroom = system.actorOf(Sensor.props("BedroomMotion", controlUnit));
        ActorRef keypad = system.actorOf(KeyPad.props(controlUnit));
        return new TestEnvironment(controlUnit, frontDoor, livingRoom, bedroom, keypad);
    }

    @Test
    public void testSirenFiresOnTimeout() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);
        final TestEnvironment env = createEnvironment(sirenProbe);

        // Arm the system (GroundFloor & Perimeter)
        env.controlUnit().tell(new SmartHomeProtocol.ArmSystemRequest(Set.of("GroundFloor", "Perimeter")), kit.testActor());

        try { Thread.sleep(150); } catch (InterruptedException e) {}

        // Trigger a sensor in an active zone
        env.livingRoomSensor().tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        // Assert the Siren fires after the fast entry delay expires
        FiniteDuration assertionTimeout = JavaDurationConverters.asFiniteDuration(Duration.ofMillis(200));
        sirenProbe.expectMsgClass(assertionTimeout, SmartHomeProtocol.ActivateSiren.class);
    }

    @Test
    public void testSuccessfulDisarmDuringEntryDelay() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);
        final TestEnvironment env = createEnvironment(sirenProbe);

        env.controlUnit().tell(new SmartHomeProtocol.ArmSystemRequest(Set.of("GroundFloor", "Perimeter", "UpperFloor")), kit.testActor());

        try { Thread.sleep(150); } catch (InterruptedException e) {}

        env.frontDoorSensor().tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());
        try { Thread.sleep(20); } catch (InterruptedException e) {}

        // Disarm via KeyPad
        env.keypad().tell(new SmartHomeProtocol.InsertPinMsg("1111"), kit.testActor());

        FiniteDuration safetyWindow = scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS);
        sirenProbe.expectNoMessage(safetyWindow);
    }

    @Test
    public void testPartialArmingIgnoresInactiveZones() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);
        final TestEnvironment env = createEnvironment(sirenProbe);

        // Night Mode: Arm ONLY Perimeter and GroundFloor (UpperFloor left inactive)
        env.controlUnit().tell(new SmartHomeProtocol.ArmSystemRequest(Set.of("Perimeter", "GroundFloor")), kit.testActor());

        try { Thread.sleep(150); } catch (InterruptedException e) {}

        // Trigger user movement upstairs in the inactive zone
        env.bedroomSensor().tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        FiniteDuration safetyWindow = scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS);
        sirenProbe.expectNoMessage(safetyWindow);
    }

    @Test
    @Description("When the system is in the Disarmed state, triggering any sensor does not trigger an entry delay or fire the siren")
    public void testSensorsIgnoredWhenDisarmed() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);
        final TestEnvironment env = createEnvironment(sirenProbe);

        env.bedroomSensor().tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        FiniteDuration safetyWindow = scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS);
        sirenProbe.expectNoMessage(safetyWindow);
    }

    @Test
    @Description("Verifies that sensors triggered while the exit delay countdown is active are ignored")
    public void testSensorsIgnoredDuringExitDelay() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);
        final TestEnvironment env = createEnvironment(sirenProbe);

        env.controlUnit().tell(new SmartHomeProtocol.ArmSystemRequest(Set.of("Perimeter")), kit.testActor());

        // Trigger sensor immediately during exit delay countdown
        env.frontDoorSensor().tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        // Wait out the exit delay
        try { Thread.sleep(150); } catch (InterruptedException e) {}

        // Ensure siren never fired prematurely
        FiniteDuration safetyWindow = scala.concurrent.duration.Duration.create(200, TimeUnit.MILLISECONDS);
        sirenProbe.expectNoMessage(safetyWindow);
    }

    @Test
    @Description("Verifies that entering a valid PIN during the Alarm state deactivates the siren and disarms the system")
    public void testAlarmStopsWithValidPin() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);
        final TestEnvironment env = createEnvironment(sirenProbe);

        env.controlUnit().tell(new SmartHomeProtocol.ArmSystemRequest(Set.of("Perimeter")), kit.testActor());
        try { Thread.sleep(150); } catch (InterruptedException e) {}

        env.frontDoorSensor().tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());
        try { Thread.sleep(150); } catch (InterruptedException e) {} // Wait out entry delay to reach Alarm state

        // Verify siren activated
        sirenProbe.expectMsgClass(SmartHomeProtocol.ActivateSiren.class);

        // Enter valid PIN via keypad
        env.keypad().tell(new SmartHomeProtocol.InsertPinMsg("1111"), kit.testActor());

        // Verify siren deactivated
        sirenProbe.expectMsgClass(SmartHomeProtocol.DeactivateSiren.class);
    }

    @Test
    @Description("Verifies that entering an invalid PIN during the Alarm state does not stop the siren")
    public void testInvalidPinDuringAlarm() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);
        final TestEnvironment env = createEnvironment(sirenProbe);

        env.controlUnit().tell(new SmartHomeProtocol.ArmSystemRequest(Set.of("Perimeter")), kit.testActor());
        try { Thread.sleep(150); } catch (InterruptedException e) {}

        env.frontDoorSensor().tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());
        try { Thread.sleep(150); } catch (InterruptedException e) {}

        sirenProbe.expectMsgClass(SmartHomeProtocol.ActivateSiren.class);

        // Enter invalid PIN via keypad
        env.keypad().tell(new SmartHomeProtocol.InsertPinMsg("0000"), kit.testActor());

        // Siren should keep going (expect no DeactivateSiren message)
        FiniteDuration safetyWindow = scala.concurrent.duration.Duration.create(200, TimeUnit.MILLISECONDS);
        sirenProbe.expectNoMessage(safetyWindow);
    }

    @Test
    @Description("Verifies that full arming activates all zones, allowing sensors in upper floors to trigger the alarm")
    public void testFullArmingActivatesAllZones() {
        final TestKit kit = new TestKit(system);
        final TestProbe sirenProbe = new TestProbe(system);
        final TestEnvironment env = createEnvironment(sirenProbe);

        // Arm all zones including UpperFloor
        env.controlUnit().tell(new SmartHomeProtocol.ArmSystemRequest(Set.of("GroundFloor", "Perimeter", "UpperFloor")), kit.testActor());
        try { Thread.sleep(150); } catch (InterruptedException e) {}

        // Trigger upper floor sensor
        env.bedroomSensor().tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        // Siren should fire after entry delay
        FiniteDuration assertionTimeout = JavaDurationConverters.asFiniteDuration(Duration.ofMillis(200));
        sirenProbe.expectMsgClass(assertionTimeout, SmartHomeProtocol.ActivateSiren.class);
    }
}
