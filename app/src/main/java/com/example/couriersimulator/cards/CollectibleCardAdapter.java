package com.example.couriersimulator.cards;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup; 
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.couriersimulator.R;

import java.util.List;

/**
 * Адаптер для списка карточек велосипедов.
 * - Если пользователь не владеет карточкой, мини-изображение будет затемнено.
 * - При клике показываем диалог с большим изображением и описанием.
 */
public class CollectibleCardAdapter extends RecyclerView.Adapter<CollectibleCardAdapter.CardViewHolder> {

    private final Context context;
    private final List<CollectibleCard> cardList;

    public CollectibleCardAdapter(Context context, List<CollectibleCard> cardList) {
        this.context = context;
        this.cardList = cardList;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_collectible_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        CollectibleCard card = cardList.get(position);
        holder.bind(card);
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }

    class CardViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCardThumbnail;
        TextView tvCardName;
        TextView tvCardStatus;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCardThumbnail = itemView.findViewById(R.id.ivCardThumbnail);
            tvCardName = itemView.findViewById(R.id.tvCardName);
            tvCardStatus = itemView.findViewById(R.id.tvCardStatus);

            // Клик по элементу списка
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    CollectibleCard selectedCard = cardList.get(pos);
                    showDetailsDialog(selectedCard);
                }
            });
        }

        public void bind(CollectibleCard card) {
            tvCardName.setText(card.getName());

            if (card.isOwned()) {
                tvCardStatus.setText("Собрана");
            } else {
                tvCardStatus.setText("Не собрана");
            }

            ivCardThumbnail.setImageResource(card.getImageResId());
            // Если не владеет, затемняем картинку
            if (!card.isOwned()) {
                ivCardThumbnail.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
            } else {
                ivCardThumbnail.clearColorFilter();
            }
        }

        /**
         * Показываем диалоговое окно с большим изображением и описанием.
         */
        private void showDetailsDialog(CollectibleCard card) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            LayoutInflater inflater = LayoutInflater.from(context);
            View dialogView = inflater.inflate(R.layout.dialog_card_details, null);

            ImageView ivLarge = dialogView.findViewById(R.id.ivCardLargeImage);
            TextView tvLargeName = dialogView.findViewById(R.id.tvCardLargeName);
            TextView tvLargeDescription = dialogView.findViewById(R.id.tvCardLargeDescription);
            TextView tvOwnershipNote = dialogView.findViewById(R.id.tvCardOwnershipNote);

            // Заполняем поля
            ivLarge.setImageResource(card.getImageResId());
            tvLargeName.setText(card.getName());
            tvLargeDescription.setText(card.getDescription());

            if (card.isOwned()) {
                tvOwnershipNote.setText("У вас есть эта карточка.");
                tvOwnershipNote.setTextColor(Color.parseColor("#008000")); // зелёный
            } else {
                tvOwnershipNote.setText("Вы пока не владеете этой карточкой.");
                tvOwnershipNote.setTextColor(Color.parseColor("#FF0000")); // красный
            }

            builder.setView(dialogView);
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

            // Например, если хотите дополнительную кнопку "Использовать" (но это уже игровой сценарий)
            // builder.setNegativeButton("Использовать", (dialog, which) -> { ... });

            builder.create().show();
        }
    }
}