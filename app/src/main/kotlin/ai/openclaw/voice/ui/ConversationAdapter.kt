package ai.openclaw.voice.ui

import ai.openclaw.voice.MainViewModel.ConversationMessage
import ai.openclaw.voice.R
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ConversationAdapter : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private var messages: List<ConversationMessage> = emptyList()

    fun submitList(list: List<ConversationMessage>) {
        messages = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val root = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_message, parent, false) as FrameLayout
        return ViewHolder(root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    class ViewHolder(private val root: FrameLayout) : RecyclerView.ViewHolder(root) {
        private val bubble: MaterialCardView = root.findViewById(R.id.messageBubble)
        private val roleLabel: TextView = root.findViewById(R.id.roleLabel)
        private val messageText: TextView = root.findViewById(R.id.messageText)

        fun bind(msg: ConversationMessage) {
            val ctx = root.context
            val isUser = msg.role == "user"

            roleLabel.text = if (isUser) "You" else "Assistant"
            messageText.text = msg.text

            val density = ctx.resources.displayMetrics.density
            val margin = (64 * density).toInt()
            val params = bubble.layoutParams as FrameLayout.LayoutParams

            if (isUser) {
                params.gravity = Gravity.END
                params.marginStart = margin
                params.marginEnd = 0
                bubble.setCardBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.accent)
                )
                bubble.strokeWidth = 0
                roleLabel.setTextColor(Color.argb(180, 255, 255, 255))
                messageText.setTextColor(Color.WHITE)
            } else {
                params.gravity = Gravity.START
                params.marginStart = 0
                params.marginEnd = margin
                bubble.setCardBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.surface)
                )
                val strokePx = (1 * density).toInt().coerceAtLeast(1)
                bubble.strokeWidth = strokePx
                bubble.strokeColor = ContextCompat.getColor(ctx, R.color.accent)
                roleLabel.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                messageText.setTextColor(ContextCompat.getColor(ctx, R.color.accent))
            }
            bubble.layoutParams = params
        }
    }
}
