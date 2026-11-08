package br.com.bomgas.bina;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Renders the main screen from observed state. Read-only: never records, schedules or sends. */
final class MonitorScreen {
    enum Banner { NONE, MONITORING, DEVICE_NAME }

    private final AppCompatActivity activity;
    private final AppSettings settings;
    private final MaterialToolbar toolbar;
    private final MaterialCardView banner, callCard;
    private final TextView bannerText, callPhase, callNumber, callDetail, recentEmpty;
    private final ImageView bannerIcon, callIcon;
    private final Button bannerAction, callBack;
    private final View cardMonitoring, cardServer, cardQueue, cardLocation;
    private final RecyclerView recentList;
    private final RecentCallsAdapter recent = new RecentCallsAdapter();
    private Banner shownBanner = Banner.NONE;
    private String callNumberRaw = "";

    MonitorScreen(AppCompatActivity activity) {
        this.activity = activity;
        settings = new AppSettings(activity);
        toolbar = activity.findViewById(R.id.toolbar);
        banner = activity.findViewById(R.id.actionBanner);
        bannerText = activity.findViewById(R.id.bannerText);
        bannerIcon = activity.findViewById(R.id.bannerIcon);
        bannerAction = activity.findViewById(R.id.bannerAction);
        callCard = activity.findViewById(R.id.callCard);
        callIcon = activity.findViewById(R.id.callIcon);
        callPhase = activity.findViewById(R.id.callPhase);
        callNumber = activity.findViewById(R.id.callNumber);
        callDetail = activity.findViewById(R.id.callDetail);
        callBack = activity.findViewById(R.id.callBack);
        cardMonitoring = activity.findViewById(R.id.cardMonitoring);
        cardServer = activity.findViewById(R.id.cardServer);
        cardQueue = activity.findViewById(R.id.cardQueue);
        cardLocation = activity.findViewById(R.id.cardLocation);
        recentEmpty = activity.findViewById(R.id.recentEmpty);
        recentList = activity.findViewById(R.id.recentCalls);
        recentList.setLayoutManager(new LinearLayoutManager(activity));
        recentList.setAdapter(recent);
        callBack.setOnClickListener(v -> dial(activity, callNumberRaw));
        callCard.setOnLongClickListener(v -> copy(activity, callNumberRaw));
    }

    View monitoringCard() { return cardMonitoring; }
    View serverCard() { return cardServer; }
    View queueCard() { return cardQueue; }
    View locationCard() { return cardLocation; }
    View bannerAction() { return bannerAction; }
    Banner shownBanner() { return shownBanner; }

    void render() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        EnvironmentConfig active = settings.active();
        String name = settings.name(active);
        toolbar.setSubtitle(DeviceLabel.describe(settings));

        MonitoringState monitoring = MonitoringRecovery.state(activity);
        status(cardMonitoring, R.string.status_monitoring, monitoring.summary(), R.drawable.ic_shield);

        StatusSummary server = ConnectionStatus.classify(settings.connection(active));
        status(cardServer, R.string.status_server, server, server.tone == StatusSummary.Tone.OK ? R.drawable.ic_cloud_done
                : server.tone == StatusSummary.Tone.PROBLEM ? R.drawable.ic_cloud_off : R.drawable.ic_cloud);

        status(cardQueue, R.string.status_queue, queueSummary(), R.drawable.ic_send);

        StatusSummary tracking = LocationTracking.summary(activity);
        cardLocation.setVisibility(tracking == null ? View.GONE : View.VISIBLE);
        if (tracking != null) status(cardLocation, R.string.status_location, tracking, R.drawable.ic_location);

        renderBanner(monitoring, name);
        renderCall();
    }

    private StatusSummary queueSummary() {
        boolean storageFailure = !activity.getSharedPreferences("outbox-health", Context.MODE_PRIVATE)
                .getString("error", "").isEmpty();
        try {
            List<DurableCalls.Pending> pending = CallOutbox.get(activity).pending();
            boolean errors = false;
            for (DurableCalls.Pending item : pending) errors |= !item.error.isEmpty();
            return QueueStatus.summarize(pending.size(), errors, storageFailure);
        } catch (IOException e) {
            return QueueStatus.summarize(0, false, true);
        }
    }

    private void renderBanner(MonitoringState monitoring, String name) {
        StatusSummary summary = monitoring.summary();
        String text = null;
        StatusSummary.Tone tone = StatusSummary.Tone.PROBLEM;
        if (summary.tone == StatusSummary.Tone.PROBLEM) {
            shownBanner = Banner.MONITORING;
            text = "Chamadas não estão sendo monitoradas: faltam permissões de telefone.";
        } else if (!CallMonitorService.isRunning(activity)) {
            shownBanner = Banner.MONITORING;
            tone = StatusSummary.Tone.ATTENTION;
            text = "O serviço de monitoramento está parado.";
        } else if (name.isEmpty()) {
            shownBanner = Banner.DEVICE_NAME;
            tone = StatusSummary.Tone.ATTENTION;
            text = "Dê um nome a este aparelho para identificá-lo no computador.";
        } else {
            shownBanner = Banner.NONE;
        }
        banner.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text == null) return;
        int on = ContextCompat.getColor(activity, ToneColors.onContainer(tone));
        banner.setCardBackgroundColor(ContextCompat.getColor(activity, ToneColors.container(tone)));
        setIfChanged(bannerText, text);
        bannerText.setTextColor(on);
        bannerIcon.setImageTintList(ColorStateList.valueOf(on));
        bannerAction.setTextColor(on);
    }

    private void renderCall() {
        CallFeed feed = CallFeedStore.load(activity);
        CallFeed.Call call = feed.current(System.currentTimeMillis());
        StringBuilder signature = new StringBuilder();
        for (CallFeed.Call item : feed.recent())
            signature.append(item.startedAt).append(item.phase).append(item.number).append(';');
        recent.submit(feed.recent(), signature.toString());
        recentEmpty.setVisibility(feed.recent().isEmpty() ? View.VISIBLE : View.GONE);

        int container, on;
        String phase, detail;
        if (call == null) {
            container = R.color.bina_surface_container;
            on = R.color.bina_on_background;
            phase = activity.getString(R.string.call_waiting);
            detail = activity.getString(R.string.call_waiting_detail);
            callNumberRaw = "";
        } else {
            callNumberRaw = call.number;
            StatusSummary.Tone tone = tone(call.phase);
            container = call.phase == CallFeed.Phase.RINGING ? R.color.call_ringing_container
                    : call.phase == CallFeed.Phase.ENDED || call.phase == CallFeed.Phase.UNKNOWN
                    ? R.color.bina_surface_container : ToneColors.container(tone);
            on = call.phase == CallFeed.Phase.RINGING ? R.color.call_on_ringing_container
                    : call.phase == CallFeed.Phase.ENDED || call.phase == CallFeed.Phase.UNKNOWN
                    ? R.color.bina_on_background : ToneColors.onContainer(tone);
            switch (call.phase) {
                case RINGING:
                    phase = "Chamando agora";
                    detail = "Recebida às " + when(call.startedAt) + ". O cadastro abre no computador.";
                    break;
                case ANSWERED:
                    phase = "Em atendimento";
                    detail = "Atendida às " + when(call.answeredAt);
                    break;
                case MISSED:
                    phase = "Chamada perdida";
                    detail = when(call.startedAt) + " · ninguém atendeu";
                    break;
                case ENDED:
                    phase = "Última chamada";
                    detail = when(call.startedAt) + " · duração " + duration(call.durationSeconds);
                    break;
                default:
                    phase = "Última chamada";
                    detail = when(call.startedAt);
            }
        }
        int onColor = ContextCompat.getColor(activity, on);
        callCard.setCardBackgroundColor(ContextCompat.getColor(activity, container));
        callIcon.setImageResource(call == null ? R.drawable.ic_call_idle : icon(call.phase));
        callIcon.setImageTintList(ColorStateList.valueOf(onColor));
        callPhase.setTextColor(onColor);
        callNumber.setTextColor(onColor);
        callDetail.setTextColor(onColor);
        // Assertive only while ringing; other changes must not interrupt the operator.
        callCard.setAccessibilityLiveRegion(call != null && call.phase == CallFeed.Phase.RINGING
                ? View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE : View.ACCESSIBILITY_LIVE_REGION_POLITE);
        setIfChanged(callPhase, phase);
        setIfChanged(callDetail, detail);
        callNumber.setVisibility(call == null ? View.GONE : View.VISIBLE);
        if (call != null) setIfChanged(callNumber, PhoneFormat.display(call.number));
        callNumber.setContentDescription(call == null ? null : PhoneFormat.spoken(call.number));
        boolean canCallBack = call != null && !call.number.isEmpty() && !call.active();
        callBack.setVisibility(canCallBack ? View.VISIBLE : View.GONE);
        if (canCallBack) callBack.setContentDescription(
                activity.getString(R.string.call_back_description, PhoneFormat.spoken(call.number)));
        String description = phase + (call == null ? "" : ", " + PhoneFormat.spoken(call.number)) + ". " + detail;
        if (!description.contentEquals(callCard.getContentDescription() == null ? "" : callCard.getContentDescription()))
            callCard.setContentDescription(description);
    }

    private void status(View card, int title, StatusSummary summary, int iconRes) {
        MaterialCardView view = (MaterialCardView) card;
        int on = ContextCompat.getColor(activity, ToneColors.onContainer(summary.tone));
        view.setCardBackgroundColor(ContextCompat.getColor(activity, ToneColors.container(summary.tone)));
        ImageView icon = card.findViewById(R.id.statusIcon);
        icon.setImageResource(summary.tone == StatusSummary.Tone.PROBLEM && iconRes == R.drawable.ic_shield
                ? R.drawable.ic_warning : iconRes);
        icon.setImageTintList(ColorStateList.valueOf(on));
        TextView titleView = card.findViewById(R.id.statusTitle);
        TextView value = card.findViewById(R.id.statusValue);
        titleView.setText(title);
        titleView.setTextColor(on);
        setIfChanged(value, summary.label);
        value.setTextColor(on);
        String description = activity.getString(R.string.status_card_description, activity.getString(title), summary.label);
        if (!description.contentEquals(card.getContentDescription() == null ? "" : card.getContentDescription()))
            card.setContentDescription(description);
    }

    /** Avoids re-announcing live regions on every periodic refresh. */
    private static void setIfChanged(TextView view, String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }

    static StatusSummary.Tone tone(CallFeed.Phase phase) {
        switch (phase) {
            case RINGING: return StatusSummary.Tone.ATTENTION;
            case ANSWERED: return StatusSummary.Tone.OK;
            case MISSED: return StatusSummary.Tone.PROBLEM;
            default: return StatusSummary.Tone.NEUTRAL;
        }
    }

    static int icon(CallFeed.Phase phase) {
        switch (phase) {
            case RINGING: return R.drawable.ic_call_ringing;
            case MISSED: return R.drawable.ic_call_missed;
            default: return R.drawable.ic_call_received;
        }
    }

    static String phaseName(CallFeed.Phase phase) {
        switch (phase) {
            case RINGING: return "Chamando";
            case ANSWERED: return "Em atendimento";
            case ENDED: return "Atendida";
            case MISSED: return "Perdida";
            default: return "Recebida";
        }
    }

    static String when(long at) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(at);
        Calendar now = Calendar.getInstance();
        boolean today = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
        return new SimpleDateFormat(today ? "HH:mm" : "dd/MM HH:mm", Locale.getDefault()).format(new Date(at));
    }

    static String duration(long seconds) {
        return seconds >= 3600
                ? String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    static void dial(Context context, String number) {
        if (number == null || number.isEmpty()) {
            Toast.makeText(context, "Número oculto: não é possível retornar.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            context.startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Nenhum discador disponível neste aparelho.", Toast.LENGTH_SHORT).show();
        }
    }

    static boolean copy(Context context, String number) {
        if (number == null || number.isEmpty()) return false;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return false;
        clipboard.setPrimaryClip(ClipData.newPlainText("Número", number));
        Toast.makeText(context, R.string.number_copied, Toast.LENGTH_SHORT).show();
        return true;
    }
}
