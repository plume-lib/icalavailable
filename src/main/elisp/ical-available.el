(defvar ical-business-hours "8:30am-5pm")
(defvar ical-business-hours-phone ical-business-hours)
;; To permit evening calls.
;; (setq ical-business-hours-phone "8:30am-5pm,7:30pm-9:30pm")

;; Implementation that uses the iCal format
(defun ical-available (&optional days start-date timezone2)
  "Insert a summary of my available times from ical.
Optional prefix argument DAYS is days how many days to show (default 8).
With just C-u prefix argument, prompt for starting date and days."
  (interactive "P")
  (let* ((ical-args
          (progn
            (if (equal days '(4))
                (setq start-date (read-from-minibuffer "Start date: " nil nil nil nil "today")
                      days (read-number "Days: " 8)
                      timezone2 (read-from-minibuffer "Timezone2: ")))
            (if (not (numberp days))
                (setq days 8))
            (if (equal start-date "")
                (setq start-date nil))
            (if (equal timezone2 "")
                (setq timezone2 nil))
            (append
             (apply #'append
             (mapcar #'(lambda (fname)
                      (let ((f (expand-file-name fname)))
                        (if (file-exists-p f)
                            (list "--iCal-URL" (bbdb-string-trim (file-contents f))))))
                  '("~/private/iCal-url1" "~/private/iCal-url2" "~/private/iCal-url3")))
             (list "--days" (format "%s" days))
             (if start-date
                 (list "--date" start-date))
             (if timezone2
                 (list "--timezone2" timezone2))
             (list "--business-hours" (if timezone2
                                          ical-business-hours-phone
                                        ical-business-hours))))))
    (let ((old-point (point)))
      ;; (message "java %s" (cons "plume.ICalAvailable" ical-args))
      (insert (apply #'call-process "java" nil t nil
		     (append (list "-cp"
				   (substitute-in-file-name "$HOME/java/plume-lib/icalavailable/build/libs/icalavailable-all.jar")
				   "-Dical4j.parsing.relaxed=true"
				   "-Dical4j.parsing.relaxed=true"
				   "org.plumelib.icalavailable.ICalAvailable")
			     ical-args)))
      (if (or (= (char-before) 0) (= (char-before) 1) (= (char-before) 255))
          (delete-char -1))
      ;; Clean up an irritating warning message.
      (save-excursion
        (goto-char old-point)
        (if (looking-at "[A-Z][a-z][a-z] [0123]?[0-9], 20[0-9][0-9] .* net.fortuna.ical4j.util.Configurator <clinit>\nINFO: ical4j.properties not found.\n")
            (replace-match ""))))))
