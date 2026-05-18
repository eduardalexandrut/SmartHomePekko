import org.apache.pekko.actor.*;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestKit;
import org.apache.pekko.testkit.TestProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class SmartHomeTest {

    private ActorSystem system;

    @BeforeEach
    public void setup() {
        system = ActorSystem.create("TestAlarmSystem");
    }

    @AfterEach
    public void tearDown() {
        TestKit.shutdownActorSystem(system, scala.concurrent.duration.Duration.apply("2s"),true);
    }

    @Test
    public void testSirenFiresOnTimeout() {
        final TestKit kit = new TestKit(system);

        final TestProbe sirenProbe = new TestProbe(system);

        // ControlUnit actor and sensor
        final ActorRef controlUnit = system.actorOf(ControlUnit.props(sirenProbe.ref()));
        final ActorRef motionSensor = system.actorOf(Sensor.props("LivingRoomMotion", controlUnit));
        controlUnit.tell(new SmartHomeProtocol.ArmSystemRequest(), kit.testActor());

        // Wait out the exit delay safely
        try { Thread.sleep(31000); } catch (InterruptedException e) {}

        // Trigger the sensor
        motionSensor.tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        // Assert using the test probes or kit assertions directly
        sirenProbe.expectMsgClass(
                org.apache.pekko.util.JavaDurationConverters.asFiniteDuration(java.time.Duration.ofSeconds(16)),
                SmartHomeProtocol.ActivateSiren.class
        );
    }

    @Test
    public void testSuccessfulDisarmDuringEntryDelay() {
        final TestKit kit = new TestKit(system);

        final TestProbe sirenProbe = new TestProbe(system);
        final ActorRef controlUnit = system.actorOf(ControlUnit.props(sirenProbe.ref()));
        final ActorRef frontDoorSensor = system.actorOf(Sensor.props("FrontDoor", controlUnit));

        controlUnit.tell(new SmartHomeProtocol.ArmSystemRequest(), kit.testActor());

        // Wait out the exit delay manually in the test thread, or configure it to 1 second for tests.
        try { Thread.sleep(31000); } catch (InterruptedException e) {}

        // Simulate an intrusion event via the Sensor
        frontDoorSensor.tell(new SmartHomeProtocol.OpenDoorMsg(), kit.testActor());

        // Simulate a KeyPad entering the correct PIN right away
        controlUnit.tell(new SmartHomeProtocol.ValidPinEntered(), kit.testActor());

        // Assert that the Siren NEVER received an ActivateSiren command
        sirenProbe.expectNoMessage((FiniteDuration) Duration.apply("2s"));
    }
}
