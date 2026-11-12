package com.epictalenttrust.hr.cv;

/** Contact details read from the top of a CV. Only used to create the candidate record. */
public record CandidateDetails(String fullName, String email, String mobile, String city, String country) {
}
