package tw.org.il.dongsheng.templeapp.service;

import tw.org.il.dongsheng.templeapp.repository.DonationRepository;

public class DonationService {

    private final DonationRepository repo;

    public DonationService(DonationRepository repo) {
        this.repo = repo;
    }
}
