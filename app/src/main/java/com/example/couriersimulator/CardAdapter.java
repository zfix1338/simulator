package com.example.couriersimulator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {
    private final List<CollectibleCard> cards;
    private final CardManager cardManager;
    private final Context context;

    public CardAdapter(List<CollectibleCard> cards, CardManager cardManager, Context context) {
        this.cards = cards;
        this.cardManager = cardManager;
        this.context = context;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        CollectibleCard card = cards.get(position);
        holder.imgCard.setImageResource(card.getImageResId());
        holder.overlay.setVisibility(cardManager.isCardCollected(card.getId()) ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(v -> showCardDetails(card));
    }

    private void showCardDetails(CollectibleCard card) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_card_details, null);

        ImageView imgCard = dialogView.findViewById(R.id.imgCardDetail);
        TextView tvTitle = dialogView.findViewById(R.id.tvTitleDetail);
        TextView tvLore = dialogView.findViewById(R.id.tvLoreDetail);

        imgCard.setImageResource(card.getImageResId());
        tvTitle.setText(card.getTitle());
        tvLore.setText(card.getLore());

        builder.setView(dialogView)
                .setPositiveButton("Закрыть", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCard;
        View overlay;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCard = itemView.findViewById(R.id.imgCard);
            overlay = itemView.findViewById(R.id.overlay);
        }
    }
}