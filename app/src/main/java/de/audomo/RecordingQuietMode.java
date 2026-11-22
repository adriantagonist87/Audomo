package de.audomo;

import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.service.notification.Condition;
import android.service.notification.ConditionProviderService;
import android.service.notification.ZenPolicy;

import java.util.Map;

/** Publish only the condition belonging to Audomo's recording rule. */
public final class RecordingQuietMode extends ConditionProviderService {
    private static final Uri CONDITION = Uri.parse("condition://de.audomo/aufnahme");

    private static Condition condition(boolean active) {
        return new Condition(
                CONDITION, "Aufnahme", active ? Condition.STATE_TRUE : Condition.STATE_FALSE);
    }

    static String update(Context context, boolean active) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.isNotificationPolicyAccessGranted())
            return "Benachrichtigungstöne: Nicht-stören-Zugriff fehlt";
        try {
            ComponentName owner = new ComponentName(context, RecordingQuietMode.class);
            String id = null;
            for (Map.Entry<String, AutomaticZenRule> item :
                    manager.getAutomaticZenRules().entrySet()) {
                AutomaticZenRule rule = item.getValue();
                if (owner.equals(rule.getOwner()) && CONDITION.equals(rule.getConditionId())) {
                    if (active && !rule.isEnabled()) return "Aufnahmeregel in Android deaktiviert";
                    id = item.getKey();
                    break;
                }
            }
            if (id == null && active) {
                ZenPolicy policy =
                        new ZenPolicy.Builder()
                                .allowAlarms(true)
                                .allowMedia(true)
                                .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
                                .allowRepeatCallers(true)
                                .allowSystem(true)
                                .allowReminders(false)
                                .allowEvents(false)
                                .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
                                .allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
                                .build();
                id =
                        manager.addAutomaticZenRule(
                                new AutomaticZenRule(
                                        "Audomo-Aufnahme",
                                        owner,
                                        null,
                                        CONDITION,
                                        policy,
                                        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                                        true));
                if (id == null) return "Android hat die Aufnahmeregel abgelehnt";
            }
            if (id != null) manager.setAutomaticZenRuleState(id, condition(active));
            return "";
        } catch (RuntimeException failure) {
            return "Aufnahmeregel ist nicht verfügbar";
        }
    }

    @Override
    public void onConnected() {}

    @Override
    public void onSubscribe(Uri uri) {
        if (CONDITION.equals(uri)) {
            Audomo app = (Audomo) getApplication();
            notifyCondition(condition(app.quietRequested));
        }
    }

    @Override
    public void onUnsubscribe(Uri uri) {}
}
