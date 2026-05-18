import org.apache.pekko.actor.AbstractActor;

public class Siren extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SmartHomeProtocol.ActivateSiren.class, this::onActivateSiren)
                .match(SmartHomeProtocol.DeactivateSiren.class, this::onDeactivateSiren)
                .build();
    }

    private void onDeactivateSiren(SmartHomeProtocol.DeactivateSiren deactivateSiren) {
        System.out.println("Siren deactivated!");
    }

    private void onActivateSiren(SmartHomeProtocol.ActivateSiren activateSiren) {
        System.out.println("Siren activated!");
    }
}
