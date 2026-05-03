package com.example.quetek

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.quetek.data.Database
import com.example.quetek.databinding.ActivityRegisterBinding
import com.example.quetek.models.Program
import com.example.quetek.models.UserType
import com.example.quetek.models.Window
import com.example.quetek.utils.UserFactory
import com.google.firebase.FirebaseApp
import showFullscreenLoadingDialog
//import showLoadingDialog

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var etFirstName: EditText
    private lateinit var etEmail: EditText
    private lateinit var spinUserType: Spinner
    private lateinit var etLastName: EditText
    private lateinit var tvAddtl: TextView
    private lateinit var spinAddtl: Spinner
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvInputFeedback: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        initializeVariables() // to initialize the leteinit variables
        FirebaseApp.initializeApp(this)


        populateSpinner(spinUserType, R.array.user_type_array)
        setUserTypeSpinnerListener()
        setRegisterButtonListener()
        setInputListeners()

        btnCancel.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }


    }

    private fun initializeVariables() {
        etFirstName = binding.etFirstName
        etLastName = binding.etLastName
        etEmail = binding.etEmail
        spinUserType = binding.spinUserType
        tvAddtl = binding.tvAddtl
        spinAddtl = binding.spinAddtl
        etPassword = binding.etPassword
        etConfirmPassword = binding.etConfirmPassword
        btnSubmit = binding.btnSubmit
        btnCancel = binding.btnCancel
        tvInputFeedback = binding.tvInputFeedback
    }
    private fun checkInputs(showFeedback: Boolean = true): Boolean {
        val isAnyEmpty = etFirstName.text.isNullOrBlank()
                || etLastName.text.isNullOrBlank()
                || etEmail.text.isNullOrBlank()
                || etPassword.text.isNullOrBlank()
                || etConfirmPassword.text.isNullOrBlank()
                || spinUserType.selectedItem == "SELECT"
                || spinAddtl.selectedItem == "SELECT"

        if (isAnyEmpty) {
            if (showFeedback) tvInputFeedback.text = "All fields must not be blank."
            tvInputFeedback.visibility = if (showFeedback) View.VISIBLE else View.GONE
            return false
        }

        if (etPassword.text.toString().length < 8) {
            if (showFeedback) tvInputFeedback.text = "Password is weak."
            tvInputFeedback.visibility = if (showFeedback) View.VISIBLE else View.GONE
            return false
        }

        if (etPassword.text.toString() != etConfirmPassword.text.toString()) {
            if (showFeedback) tvInputFeedback.text = "Passwords do not match."
            tvInputFeedback.visibility = if (showFeedback) View.VISIBLE else View.GONE
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(etEmail.text.toString()).matches()) {
            if (showFeedback) tvInputFeedback.text = "Invalid email address."
            tvInputFeedback.visibility = if (showFeedback) View.VISIBLE else View.GONE
            return false
        }

        if (!isNetworkConnected()) {
            if (showFeedback) tvInputFeedback.text = "No internet connection available."
            tvInputFeedback.visibility = if (showFeedback) View.VISIBLE else View.GONE
            return false;
        }

        tvInputFeedback.visibility = View.GONE
        return true
    }

    private fun setRegisterButtonListener() {
        btnSubmit.setOnClickListener {
            if (checkInputs()) {
                val loadingDialog = showFullscreenLoadingDialog()
                Log.e("Register", "Register button is clicked")
                Database().generateAndSaveUser(
                    onSuccess = { newId ->
                        val selectedUserType =
                            UserType.valueOf(spinUserType.selectedItem.toString())

                        val user = UserFactory.createUser(
                            id = newId,
                            password = etPassword.text.toString().trim(),
                            firstName = etFirstName.text.toString().trim(),
                            lastName = etLastName.text.toString().trim(),
                            userType = selectedUserType,
                            programOrWindow = if (selectedUserType == UserType.STUDENT)
                                Program.fromDisplayName(spinAddtl.selectedItem.toString()) else
                                Window.valueOf(spinAddtl.selectedItem.toString()),
                            isPriority = false
                            )

                        val databaseReference = Database().users
                        val userKey = databaseReference.push().key ?: return@generateAndSaveUser

                        databaseReference.child(userKey).setValue(user)
                            .addOnSuccessListener {
                                loadingDialog.dismiss()
                                showID(user.id)
                            }
                            .addOnFailureListener { error ->
                                loadingDialog.dismiss()
                            }
                    },
                    onFailure = { error ->
                        loadingDialog.dismiss()
                        Log.e("Firebase", "Failed to save user", error)
                        Toast.makeText(
                            this,
                            "Failed to generate ID: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    private fun populateSpinner(spinner: Spinner, arrayResId: Int) {
        val adapter = ArrayAdapter.createFromResource(
            this,
            arrayResId,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun populateSpinner(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            items
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }


    private fun setUserTypeSpinnerListener() {
        spinUserType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedText = parent?.getItemAtPosition(position).toString()
                if (selectedText == "SELECT") {
                    binding.llAdditional.visibility = View.GONE
                    return
                }
                val selectedType = UserType.valueOf(selectedText)

                when (selectedType) {
                    UserType.STUDENT -> {
                        tvAddtl.text = "Program"
                        populateSpinner(spinAddtl, Program.getDisplayNames())
                        binding.llAdditional.visibility = View.VISIBLE
                    }
                    UserType.ACCOUNTANT -> {
                        tvAddtl.text = "Window"
                        populateSpinner(spinAddtl, R.array.window_array)
                        binding.llAdditional.visibility = View.VISIBLE
                    }
                    else -> {
                        tvAddtl.text = ""
                        binding.llAdditional.visibility = View.GONE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                tvAddtl.text = ""
                spinAddtl.visibility = View.GONE
            }
        }

    }

    private fun setInputListeners() {
        etFirstName.addInputChangedListener()
        etLastName.addInputChangedListener()
        etEmail.addInputChangedListener()
        etPassword.addInputChangedListener()
        etConfirmPassword.addInputChangedListener()

        spinAddtl.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                checkInputs(false)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun EditText.addInputChangedListener() {
        this.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkInputs(false) // Silent check to hide feedback
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }

    private fun showID(id: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_registration_id, null)
        dialogView.findViewById<TextView>(R.id.id).text = id
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val btnOkay = dialogView.findViewById<Button>(R.id.btnOkay)

        dialog.window?.setBackgroundDrawable(getDrawable(R.drawable.rectanglelogoutdialog))



        dialog.setCancelable(false)
        dialog.show()
        btnOkay.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)

            dialog.dismiss()
            finish()
        }



    }
}
