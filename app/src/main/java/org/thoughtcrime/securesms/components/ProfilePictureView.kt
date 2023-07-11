package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.bumptech.glide.load.engine.DiskCacheStrategy
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewProfilePictureBinding
import org.session.libsession.avatars.ContactColors
import org.session.libsession.avatars.PlaceholderAvatarPhoto
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.avatars.ResourceContactPhoto
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideRequests

class ProfilePictureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {
    private val binding: ViewProfilePictureBinding by lazy { ViewProfilePictureBinding.bind(this) }
    lateinit var glide: GlideRequests
    var publicKey: String? = null
    var displayName: String? = null
    var additionalPublicKey: String? = null
    var additionalDisplayName: String? = null
    var isLarge = false

    private val profilePicturesCache = mutableMapOf<View, Recipient>()
    private val unknownRecipientDrawable = ResourceContactPhoto(R.drawable.ic_profile_default)
        .asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context), false)
    private val unknownOpenGroupDrawable = ResourceContactPhoto(R.drawable.ic_notification)
        .asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context), false)

    // endregion

    // region Updating
    fun update(recipient: Recipient) {
        fun getUserDisplayName(publicKey: String): String {
            val contact = DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(publicKey)
            return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
        }

        if (recipient.isClosedGroupRecipient) {
            val members = DatabaseComponent.get(context).groupDatabase()
                    .getGroupMemberAddresses(recipient.address.toGroupString(), true)
                    .sorted()
                    .take(2)
                    .toMutableList()
            val pk = members.getOrNull(0)?.serialize() ?: ""
            publicKey = pk
            displayName = getUserDisplayName(pk)
            val apk = members.getOrNull(1)?.serialize() ?: ""
            additionalPublicKey = apk
            additionalDisplayName = getUserDisplayName(apk)
        } else if(recipient.isOpenGroupInboxRecipient) {
            val publicKey = GroupUtil.getDecodedOpenGroupInbox(recipient.address.serialize())
            this.publicKey = publicKey
            displayName = getUserDisplayName(publicKey)
            additionalPublicKey = null
        } else {
            val publicKey = recipient.address.toString()
            this.publicKey = publicKey
            displayName = getUserDisplayName(publicKey)
            additionalPublicKey = null
        }
        update()
    }

    fun update() {
        if (!this::glide.isInitialized) return
        val publicKey = publicKey ?: return
        val additionalPublicKey = additionalPublicKey
        if (additionalPublicKey != null) {
            setProfilePictureIfNeeded(binding.doubleModeImageView1, publicKey, displayName)
            setProfilePictureIfNeeded(binding.doubleModeImageView2, additionalPublicKey, additionalDisplayName)
            binding.doubleModeImageViewContainer.visibility = View.VISIBLE
        } else {
            glide.clear(binding.doubleModeImageView1)
            glide.clear(binding.doubleModeImageView2)
            binding.doubleModeImageViewContainer.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && !isLarge) {
            setProfilePictureIfNeeded(binding.singleModeImageView, publicKey, displayName)
            binding.singleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(binding.singleModeImageView)
            binding.singleModeImageView.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && isLarge) {
            setProfilePictureIfNeeded(binding.largeSingleModeImageView, publicKey, displayName)
            binding.largeSingleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(binding.largeSingleModeImageView)
            binding.largeSingleModeImageView.visibility = View.INVISIBLE
        }
    }

    private fun setProfilePictureIfNeeded(imageView: ImageView, publicKey: String, displayName: String?) {
        if (publicKey.isNotEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
            if (profilePicturesCache[imageView] == recipient) return
            val signalProfilePicture = recipient.contactPhoto
            val avatar = (signalProfilePicture as? ProfileContactPhoto)?.avatarObject

            glide.clear(imageView)

            if (signalProfilePicture != null && avatar != "0" && avatar != "") {
                glide.load(signalProfilePicture)
                    .placeholder(unknownRecipientDrawable)
                    .centerCrop()
                    .error(unknownRecipientDrawable)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(imageView)
            } else if (recipient.isOpenGroupRecipient && recipient.groupAvatarId == null) {
                imageView.setImageDrawable(unknownOpenGroupDrawable)
            } else {
                val placeholder = PlaceholderAvatarPhoto(context, publicKey, displayName ?: "${publicKey.take(4)}...${publicKey.takeLast(4)}")
                glide.load(placeholder)
                    .placeholder(unknownRecipientDrawable)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE).circleCrop().into(imageView)
            }
            profilePicturesCache[imageView] = recipient
        } else {
            imageView.setImageDrawable(null)
        }
    }

    fun recycle() {
        profilePicturesCache.clear()
    }
    // endregion
}
