//hi-lighting user tab
var sidePanelTabs = document.querySelectorAll(".task-link.task-link-no-confirm");
const currentLocation = window.location.href;
    sidePanelTabs.forEach(function (tab){
        if(tab.href===currentLocation){
           tab.classList.add('task-link--active');
        }
    });

//Function to select all boxes in filtered/original table
var mainCheckbox = document.getElementById("selectAllCheckbox");
mainCheckbox.addEventListener("click", function () {
    var visibleCheckboxes = document.querySelectorAll('.user-row:not([style*="display: none"]) input[name="selectedUsers"]');
    var isChecked = mainCheckbox.checked;
    visibleCheckboxes.forEach(function(checkbox) {
        checkbox.checked = isChecked;
    });
});

//filtering users in table from search bar
function filterTable() {
         var input = document.getElementById("userSearch");
         var filter = input.value.toLowerCase();
         var rows = document.querySelectorAll(".user-row");

         rows.forEach(function(row) {
               var name = row.querySelector(".user-name").textContent.toLowerCase();
               var id = row.querySelector(".user-id").textContent.toLowerCase();
               if (name.includes(filter) || id.includes(filter)) {
                   row.style.display = "";
               }
               else {
                  row.style.display = "none";
               }
         });
}

//prevent form submission.
const form = document.getElementById("Form");
form.addEventListener("submit", function (event) {
    event.preventDefault();
});

//bottom alert banner for premium features (bulk actions)
document.addEventListener("DOMContentLoaded", function () {
    // Only show premium banner for bulk action button
    var bulkActionButton = document.querySelector('.jenkins-button');
    var banner = document.getElementById('premiumBanner');

    if (bulkActionButton && banner) {
        bulkActionButton.addEventListener('click', function (event) {
            event.preventDefault();
            banner.style.display = 'block';
            setTimeout(function () {
                banner.style.display = 'none';
            }, 3000);
        });
    }

    // Check for success/error messages in URL parameters
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('success')) {
        showSuccessBanner();
    } else if (urlParams.has('error')) {
        showErrorBanner();
    }
});

// Show success banner
function showSuccessBanner() {
    var successBanner = document.getElementById('successBanner');
    if (successBanner) {
        successBanner.style.display = 'block';
        setTimeout(function () {
            successBanner.style.display = 'none';
        }, 5000);
    }
}

// Show error banner
function showErrorBanner() {
    var errorBanner = document.getElementById('errorBanner');
    if (errorBanner) {
        errorBanner.style.display = 'block';
        setTimeout(function () {
            errorBanner.style.display = 'none';
        }, 5000);
    }
}