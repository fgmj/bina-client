package br.com.bomgas.bina;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** "Últimas chamadas": tap dials back, long press copies the number. */
final class RecentCallsAdapter extends RecyclerView.Adapter<RecentCallsAdapter.Holder> {
    private final List<CallFeed.Call> calls = new ArrayList<>();
    private String signature = "";

    void submit(List<CallFeed.Call> latest, String latestSignature) {
        if (latestSignature.equals(signature)) return;
        signature = latestSignature;
        calls.clear();
        calls.addAll(latest);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_call, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CallFeed.Call call = calls.get(position);
        View item = holder.itemView;
        StatusSummary.Tone tone = MonitorScreen.tone(call.phase);
        ViewCompat.setBackgroundTintList(holder.badge,
                ColorStateList.valueOf(ContextCompat.getColor(item.getContext(), ToneColors.container(tone))));
        holder.icon.setImageResource(MonitorScreen.icon(call.phase));
        holder.icon.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(item.getContext(), ToneColors.onContainer(tone))));
        holder.number.setText(PhoneFormat.display(call.number));
        String detail = MonitorScreen.phaseName(call.phase) + " · " + MonitorScreen.when(call.startedAt)
                + (call.phase == CallFeed.Phase.ENDED ? " · " + MonitorScreen.duration(call.durationSeconds) : "");
        holder.detail.setText(detail);
        item.setContentDescription(MonitorScreen.phaseName(call.phase) + ", " + PhoneFormat.spoken(call.number)
                + ", " + MonitorScreen.when(call.startedAt)
                + (call.number.isEmpty() ? "" : ". Toque para retornar, toque longo para copiar."));
        item.setOnClickListener(v -> MonitorScreen.dial(v.getContext(), call.number));
        item.setOnLongClickListener(v -> MonitorScreen.copy(v.getContext(), call.number));
    }

    @Override
    public int getItemCount() { return calls.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final View badge;
        final ImageView icon;
        final TextView number, detail;

        Holder(View view) {
            super(view);
            badge = view.findViewById(R.id.recentBadge);
            icon = view.findViewById(R.id.recentIcon);
            number = view.findViewById(R.id.recentNumber);
            detail = view.findViewById(R.id.recentDetail);
        }
    }
}
