package tw.org.il.dongsheng.templeapp.model;

import java.util.List;

public record MemberBatchUpdateRequest(
        List<Integer> memberIds,
        String phone,
        String zipCode,
        String address,
        boolean updatePhone,
        boolean updateAddress
) {
    public MemberBatchUpdateRequest {
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
    }
}
