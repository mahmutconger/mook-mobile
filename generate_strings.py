import json
import os

data1 = {
  "discover_error_couldnt_load_profiles": "Couldn't load profiles",
  "discover_retry": "Retry",
  "discover_no_more_profiles": "No more profiles",
  "discover_liked_me_title": "Bu kişi sizi beğendi ✨",
  "discover_liked_me_body": "Siz de onu beğenerek eşleşebilir ve hemen mesajlaşmaya başlayabilirsiniz.",
  "discover_liked_me_dismiss": "Anladım",
  "discover_block_user": "Block User",
  "discover_block_user_message": "Are you sure you want to block this user? They will no longer see you, and you will no longer see them in your feed.",
  "discover_block_confirm": "Block",
  "discover_block_cancel": "Cancel",
  "profile_block_user_title": "Kullanıcıyı Engelle",
  "profile_block_user_message": "Bu kullanıcıyı engellersen bir daha profilini göremezsin ve seni bulamazlar.",
  "profile_block_confirm": "Engelle",
  "profile_block_cancel": "İptal",
  "profile_error_loading": "Error loading profile",
  "profile_details_title": "Details",
  "profile_country": "Country",
  "profile_country_empty": "—",
  "profile_language": "Language",
  "profile_language_empty": "—",
  "profile_about_me": "About Me",
  "profile_no_description": "No description provided.",
  "profile_interests": "Interests",
  "profile_send_message": "💬 Send Message",
  "profile_block_user": "🚫 Kullanıcıyı Engelle",
  "match_title": "It's a Match!",
  "match_liked_each_other": "You and %1$s liked each other",
  "match_chat_walktalk": "💬 Chat on WalkTalk",
  "match_keep_swiping": "Keep Swiping",
  "swipeable_card_liked_me": "Sizi beğendi ✨",
  "swipeable_card_report_profile": "Report Profile",
  "swipeable_card_block_user": "Block User"
}

data2 = {
  "login_app_name": "Mook",
  "login_subtitle": "Connect. Learn. Match.",
  "login_email_label": "EMAIL",
  "login_email_placeholder": "Enter your email",
  "login_password_label": "PASSWORD",
  "login_password_placeholder": "Enter your password",
  "login_hide_password_cd": "Hide password",
  "login_show_password_cd": "Show password",
  "login_forgot_password": "Forgot Password?",
  "login_sign_in_button": "Sign In",
  "login_dont_have_account": "Don't have an account? ",
  "login_sign_up": "Sign Up",
  "onboarding_step_indicator": "STEP 1 OF 3",
  "onboarding_title": "Select Languages",
  "onboarding_subtitle": "Choose the languages you want to practice tonight.",
  "onboarding_native_language_label": "Native Language",
  "onboarding_select_native_language": "Select Native Language",
  "onboarding_swap_languages_cd": "Swap languages",
  "onboarding_target_language_label": "Target Language",
  "onboarding_select_target_language": "Select Target Language",
  "onboarding_target_level_label": "Target Level",
  "onboarding_continue_button": "Continue",
  "settings_delete_account_title": "Hesabı Sil",
  "settings_delete_account_message": "Hesabını kalıcı olarak silmek istediğinden emin misin? Profil bilgilerin, fotoğrafların ve tüm eşleşmelerin silinecek. Bu işlem geri alınamaz.",
  "settings_delete_account_confirm": "Evet, Sil",
  "settings_delete_account_cancel": "İptal",
  "settings_title": "Settings",
  "settings_language_dialog_title": "Uygulama Dili / App Language",
  "settings_language_dialog_close": "Kapat / Close",
  "settings_account_section": "ACCOUNT",
  "settings_email_label": "Email",
  "settings_appearance_section": "APPEARANCE",
  "settings_dark_mode_title": "Koyu Tema",
  "settings_dark_mode_subtitle": "Uygulama genelinde koyu tema kullan",
  "settings_app_language_label": "Uygulama Dili / App Language",
  "settings_discovery_section": "DISCOVERY",
  "settings_show_in_discover_title": "Show me in Discover",
  "settings_show_in_discover_subtitle": "Let others discover your profile",
  "settings_age_preferences_label": "Age preferences",
  "settings_legal_section": "LEGAL",
  "settings_privacy_policy_label": "Privacy Policy & Terms of Use",
  "settings_view_label": "View",
  "settings_logout_button": "Çıkış Yap",
  "settings_delete_account_button": "Hesabı Kalıcı Olarak Sil",
  "registration_date_picker_ok": "OK",
  "registration_date_picker_cancel": "Cancel",
  "registration_age_blocked_error": "Sorry, you are not eligible to use Mook at this time.",
  "registration_step1_title": "When is your birthday?",
  "registration_step1_subtitle": "Please enter your date of birth",
  "registration_step2_title": "Your languages",
  "registration_step2_subtitle": "The language you speak and where you're from",
  "registration_step3_title": "About you",
  "registration_step3_subtitle": "Your name and gender",
  "registration_step4_title": "Finish signing up",
  "registration_step4_subtitle": "Add your photos and account details",
  "registration_create_account": "Create Account",
  "registration_continue": "Continue",
  "registration_date_of_birth_label": "DATE OF BIRTH",
  "registration_select_birth_date": "Select your birth date",
  "registration_language_label": "LANGUAGE",
  "registration_country_label": "COUNTRY",
  "registration_full_name_label": "FULL NAME",
  "registration_full_name_placeholder": "Enter your full name",
  "registration_gender_label": "GENDER",
  "registration_gender_male": "Male",
  "registration_gender_female": "Female",
  "registration_gender_other": "Other",
  "registration_email_label": "EMAIL",
  "registration_email_placeholder": "Enter your email",
  "registration_password_label": "PASSWORD",
  "registration_password_placeholder": "Create a password",
  "registration_confirm_password_label": "CONFIRM PASSWORD",
  "registration_confirm_password_placeholder": "Re-enter your password",
  "registration_discovery_photos_label": "DISCOVERY PHOTOS (At least 1 required)",
  "registration_discovery_photo_cd": "Discovery photo",
  "registration_bio_label": "BIO",
  "registration_bio_placeholder": "Tell us about yourself...",
  "registration_interests_label": "INTERESTS",
  "registration_show_in_discover_title": "Show me in Discover",
  "registration_show_in_discover_subtitle": "Let others discover your profile",
  "registration_read_privacy_policy": "Read Privacy Policy & Terms of Use",
  "registration_eula_accept_text": "I have read and agree to the Mook Terms of Service and Privacy Policy. I understand that Mook has zero tolerance for objectionable content and abusive users, and any such behavior will result in a permanent ban."
}

merged_data = {**data1, **data2}
file_path = "shared/src/commonMain/composeResources/values/strings.xml"

with open(file_path, "r") as f:
    content = f.read()

new_strings = []
for key, value in merged_data.items():
    if f'name="{key}"' not in content:
        # Escape single quotes, double quotes and ampersands
        escaped_value = value.replace("&", "&amp;").replace("'", "\\'").replace('"', '\\"').replace("<", "&lt;").replace(">", "&gt;")
        new_strings.append(f'    <string name="{key}">{escaped_value}</string>')

if new_strings:
    insertion = "\n".join(new_strings) + "\n</resources>"
    new_content = content.replace("</resources>", insertion)
    with open(file_path, "w") as f:
        f.write(new_content)

print(f"Added {len(new_strings)} strings.")
