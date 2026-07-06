package com.yosefario.nclientv3.adapters;

import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.api.comments.Comment;
import com.yosefario.nclientv3.api.comments.CommentDeleter;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.ImageDownloadUtility;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {
    private final List<Comment> comments;
    private final DateFormat format;
    private final int userId;
    private final AppCompatActivity context;

    public CommentAdapter(AppCompatActivity context, List<Comment> comments) {
        this.context = context;
        format = android.text.format.DateFormat.getDateFormat(context);
        this.comments = comments == null ? new ArrayList<>() : comments;
        if (Login.isLogged() && Login.getUser() != null) {
            userId = Login.getUser().getId();
        } else userId = -1;
    }

    @NonNull
    @Override
    public CommentAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.comment_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CommentAdapter.ViewHolder holder, int pos) {
        int position = holder.getBindingAdapterPosition();
        Comment c = comments.get(position);
        holder.layout.setOnClickListener(v1 -> {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                context.runOnUiThread(() -> holder.body.setMaxLines(holder.body.getMaxLines() == 7 ? 999 : 7));
            }
        });
        holder.close.setVisibility(c.getPosterId() != userId ? View.GONE : View.VISIBLE);
        holder.user.setText(c.getUsername());
        holder.body.setText(c.getComment());
        holder.date.setText(format.format(c.getPostDate()));
        holder.close.setOnClickListener(v -> {
            int clickPos = holder.getBindingAdapterPosition();
            if (clickPos == RecyclerView.NO_POSITION) return;
            Comment target = comments.get(clickPos);
            new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_comment)
                .setMessage(R.string.delete_comment_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteComment(target))
                .setNegativeButton(R.string.cancel, null)
                .show();
        });
        if (c.getAvatarUrl() == null || Global.getDownloadPolicy() != Global.DataUsageType.FULL)
            ImageDownloadUtility.loadImage(R.drawable.ic_person, holder.userImage);
        else
            ImageDownloadUtility.loadImage(context, c.getAvatarUrl(), holder.userImage);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void addComment(Comment c) {
        comments.add(0, c);
        context.runOnUiThread(() -> notifyItemInserted(0));
    }

    private void deleteComment(@NonNull Comment target) {
        CommentDeleter.delete(target.getId(), new CommentDeleter.Callback() {
            @Override
            public void onSuccess() {
                context.runOnUiThread(() -> {
                    int idx = comments.indexOf(target);
                    if (idx >= 0) {
                        comments.remove(idx);
                        notifyItemRemoved(idx);
                    }
                    Toast.makeText(context, R.string.comment_deleted, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(@NonNull String message) {
                context.runOnUiThread(() -> Toast.makeText(context,
                    context.getString(R.string.comment_delete_failed, message), Toast.LENGTH_LONG).show());
            }
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView userImage;
        final ImageView close;
        final TextView user;
        final TextView body;
        final TextView date;
        final ConstraintLayout layout;

        public ViewHolder(@NonNull View v) {
            super(v);
            layout = v.findViewById(R.id.master_layout);
            userImage = v.findViewById(R.id.propic);
            close = v.findViewById(R.id.close);
            user = v.findViewById(R.id.username);
            body = v.findViewById(R.id.body);
            date = v.findViewById(R.id.date);
        }
    }
}
