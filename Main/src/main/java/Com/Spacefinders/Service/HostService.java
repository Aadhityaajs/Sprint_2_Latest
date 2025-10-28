package Com.Spacefinders.Service;

import Com.Spacefinders.DTO.Request.ComplaintRequest;
import Com.Spacefinders.DTO.Request.PropertyRequest;
import Com.Spacefinders.DTO.Request.PropertyUpdateRequest;
import Com.Spacefinders.DTO.Response.BookingResponse;
import Com.Spacefinders.DTO.Response.ComplaintResponse;
import Com.Spacefinders.DTO.Response.PropertyDetailResponse;
import Com.Spacefinders.DTO.Response.PropertyResponse;
import Com.Spacefinders.Entity.*;
import Com.Spacefinders.Enums.*;
import Com.Spacefinders.Exception.*;
import Com.Spacefinders.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class HostService {

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    // Add Property
    public PropertyResponse addProperty(PropertyRequest request) {
        // Validate user exists
        User host = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Host not found"));

        // Check if user is a HOST
        if (host.getUserRole() != UserRole.HOST) {
            throw new UnauthorizedException("Only hosts can add properties");
        }

        // Create address
        Address address = new Address();
        address.setBuildingNo(request.getBuildingNo());
        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPostalCode(request.getPostalCode());

        Address savedAddress = addressRepository.save(address);

        // Create property
        Property property = new Property();
        property.setPropertyName(request.getPropertyName());
        property.setPropertyDescription(request.getPropertyDescription());
        property.setNoOfRooms(request.getNoOfRooms());
        property.setNoOfBathrooms(request.getNoOfBathrooms());
        property.setMaxNoOfGuests(request.getMaxNoOfGuests());
        property.setPricePerDay(request.getPricePerDay());
        property.setUserId(request.getUserId());
        property.setImageURL(request.getImageURL());
        property.setAddressId(savedAddress.getAddressId());
        property.setHasWifi(request.getHasWifi());
        property.setHasParking(request.getHasParking());
        property.setHasPool(request.getHasPool());
        property.setHasAc(request.getHasAc());
        property.setHasHeater(request.getHasHeater());
        property.setHasPetFriendly(request.getHasPetFriendly());
        property.setPropertyStatus(PropertyStatus.AVAILABLE);
        property.setPropertyRate(0.0);
        property.setPropertyRatingCount(0);

        Property savedProperty = propertyRepository.save(property);

        return convertToPropertyResponse(savedProperty);
    }

    // Update Property
    public PropertyResponse updateProperty(PropertyUpdateRequest request) {
        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new PropertyNotFoundException("Property not found"));

        // Update property details
        property.setPropertyName(request.getPropertyName());
        property.setPropertyDescription(request.getPropertyDescription());
        property.setNoOfRooms(request.getNoOfRooms());
        property.setNoOfBathrooms(request.getNoOfBathrooms());
        property.setMaxNoOfGuests(request.getMaxNoOfGuests());
        property.setPricePerDay(request.getPricePerDay());
        property.setImageURL(request.getImageURL());
        property.setHasWifi(request.getHasWifi());
        property.setHasParking(request.getHasParking());
        property.setHasPool(request.getHasPool());
        property.setHasAc(request.getHasAc());
        property.setHasHeater(request.getHasHeater());
        property.setHasPetFriendly(request.getHasPetFriendly());
        property.setPropertyStatus(PropertyStatus.valueOf(request.getPropertyStatus()));

        // Update address
        Address address = addressRepository.findById(property.getAddressId())
                .orElseThrow(() -> new RuntimeException("Address not found"));

        address.setBuildingNo(request.getBuildingNo());
        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPostalCode(request.getPostalCode());

        addressRepository.save(address);

        Property updatedProperty = propertyRepository.save(property);

        return convertToPropertyResponse(updatedProperty);
    }

    // View All Properties by Host
    public List<PropertyResponse> viewAllProperty(Long userId) {
        List<Property> properties = propertyRepository.findByUserId(userId);

        // Filter out DELETED properties
        List<PropertyResponse> propertyResponses = new ArrayList<>();
        for (Property property : properties) {
            if (property.getPropertyStatus() != PropertyStatus.DELETED) {
                propertyResponses.add(convertToPropertyResponse(property));
            }
        }

        return propertyResponses;
    }

    // View Property by ID
    public PropertyDetailResponse viewPropertyById(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found"));

        Address address = addressRepository.findById(property.getAddressId())
                .orElseThrow(() -> new RuntimeException("Address not found"));

        User host = userRepository.findById(property.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Host not found"));

        return convertToPropertyDetailResponse(property, address, host);
    }

    // Delete Property (Soft Delete)
    public void deleteProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found"));

        // Check if property is already deleted
        if (property.getPropertyStatus() == PropertyStatus.DELETED) {
            throw new InvalidOperationException("Property is already deleted");
        }

        // Check if property status is AVAILABLE
        if (property.getPropertyStatus() != PropertyStatus.AVAILABLE) {
            throw new InvalidOperationException("Only available properties can be deleted");
        }

        // Check for active bookings
        List<Booking> activeBookings = bookingRepository.findByPropertyId(propertyId);
        for (Booking booking : activeBookings) {
            if (booking.getIsBookingStatus() == BookingStatus.CONFIRMED ||
                    booking.getIsBookingStatus() == BookingStatus.PENDING) {
                throw new InvalidOperationException("Cannot delete property with active bookings");
            }
        }

        // Soft delete
        property.setPropertyStatus(PropertyStatus.DELETED);
        propertyRepository.save(property);
    }

    // View Deleted Properties
    public List<PropertyResponse> viewDeleteProperty(Long userId) {
        List<Property> properties = propertyRepository.findByUserIdAndPropertyStatus(
                userId, PropertyStatus.DELETED
        );

        List<PropertyResponse> propertyResponses = new ArrayList<>();
        for (Property property : properties) {
            propertyResponses.add(convertToPropertyResponse(property));
        }

        return propertyResponses;
    }

    // View Bookings for Host's Properties
    public List<BookingResponse> viewBookings(Long userId) {
        List<Booking> bookings = bookingRepository.findBookingsByHostId(userId);

        List<BookingResponse> bookingResponses = new ArrayList<>();
        for (Booking booking : bookings) {
            bookingResponses.add(convertToBookingResponse(booking));
        }

        return bookingResponses;
    }

    // Add Complaint
    public ComplaintResponse addComplaint(ComplaintRequest request) {
        // Check if booking exists
        if (request.getBookingId() != null) {
            Booking booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        }

        // Create complaint
        Complaint complaint = new Complaint();
        complaint.setUserId(request.getUserId());
        complaint.setBookingId(request.getBookingId());
        complaint.setComplaintDescription(request.getComplaintDescription());
        complaint.setComplaintType(ComplaintType.valueOf(request.getComplaintType()));
        complaint.setComplaintStatus(ComplaintStatus.PENDING);
        complaint.setComplaintDate(LocalDateTime.now());

        Complaint savedComplaint = complaintRepository.save(complaint);

        return convertToComplaintResponse(savedComplaint);
    }

    // Helper method to convert Property to PropertyResponse
    private PropertyResponse convertToPropertyResponse(Property property) {
        PropertyResponse response = new PropertyResponse();
        response.setPropertyId(property.getPropertyId());
        response.setPropertyName(property.getPropertyName());
        response.setPropertyDescription(property.getPropertyDescription());
        response.setNoOfRooms(property.getNoOfRooms());
        response.setNoOfBathrooms(property.getNoOfBathrooms());
        response.setMaxNoOfGuests(property.getMaxNoOfGuests());
        response.setPricePerDay(property.getPricePerDay());
        response.setImageURL(property.getImageURL());
        response.setPropertyStatus(property.getPropertyStatus().toString());
        response.setPropertyRate(property.getPropertyRate());
        response.setPropertyRatingCount(property.getPropertyRatingCount());
        response.setHasWifi(property.getHasWifi());
        response.setHasParking(property.getHasParking());
        response.setHasPool(property.getHasPool());
        response.setHasAc(property.getHasAc());
        response.setHasHeater(property.getHasHeater());
        response.setHasPetFriendly(property.getHasPetFriendly());

        // Get address details
        Address address = addressRepository.findById(property.getAddressId()).orElse(null);
        if (address != null) {
            response.setCity(address.getCity());
            response.setState(address.getState());
            response.setCountry(address.getCountry());
        }

        return response;
    }

    // Helper method to convert to PropertyDetailResponse
    private PropertyDetailResponse convertToPropertyDetailResponse(Property property, Address address, User host) {
        PropertyDetailResponse response = new PropertyDetailResponse();
        response.setPropertyId(property.getPropertyId());
        response.setPropertyName(property.getPropertyName());
        response.setPropertyDescription(property.getPropertyDescription());
        response.setNoOfRooms(property.getNoOfRooms());
        response.setNoOfBathrooms(property.getNoOfBathrooms());
        response.setMaxNoOfGuests(property.getMaxNoOfGuests());
        response.setPricePerDay(property.getPricePerDay());
        response.setImageURL(property.getImageURL());
        response.setPropertyStatus(property.getPropertyStatus().toString());
        response.setPropertyRate(property.getPropertyRate());
        response.setPropertyRatingCount(property.getPropertyRatingCount());
        response.setHasWifi(property.getHasWifi());
        response.setHasParking(property.getHasParking());
        response.setHasPool(property.getHasPool());
        response.setHasAc(property.getHasAc());
        response.setHasHeater(property.getHasHeater());
        response.setHasPetFriendly(property.getHasPetFriendly());

        // Address details
        response.setBuildingNo(address.getBuildingNo());
        response.setStreet(address.getStreet());
        response.setCity(address.getCity());
        response.setState(address.getState());
        response.setCountry(address.getCountry());
        response.setPostalCode(address.getPostalCode());

        // Host details
        response.setHostId(host.getUserId());
        response.setHostName(host.getUsername());
        response.setHostPhone(host.getUserPhone());

        return response;
    }

    // Helper method to convert Booking to BookingResponse
    private BookingResponse convertToBookingResponse(Booking booking) {
        BookingResponse response = new BookingResponse();
        response.setBookingId(booking.getBookingId());
        response.setPropertyId(booking.getPropertyId());
        response.setUserId(booking.getUserId());
        response.setCheckinDate(booking.getCheckinDate());
        response.setCheckoutDate(booking.getCheckoutDate());
        response.setIsPaymentStatus(booking.getIsPaymentStatus());
        response.setIsBookingStatus(booking.getIsBookingStatus().toString());
        response.setHasExtraCot(booking.getHasExtraCot());
        response.setHasDeepClean(booking.getHasDeepClean());

        // Get property details
        Property property = propertyRepository.findById(booking.getPropertyId()).orElse(null);
        if (property != null) {
            response.setPropertyName(property.getPropertyName());
            response.setPropertyImage(property.getImageURL());

            Address address = addressRepository.findById(property.getAddressId()).orElse(null);
            if (address != null) {
                response.setCity(address.getCity());
            }
        }

        // Get user details
        User user = userRepository.findById(booking.getUserId()).orElse(null);
        if (user != null) {
            response.setUsername(user.getUsername());
        }

        return response;
    }

    // Helper method to convert Complaint to ComplaintResponse
    private ComplaintResponse convertToComplaintResponse(Complaint complaint) {
        ComplaintResponse response = new ComplaintResponse();
        response.setComplaintId(complaint.getComplaintId());
        response.setUserId(complaint.getUserId());
        response.setBookingId(complaint.getBookingId());
        response.setComplaintDescription(complaint.getComplaintDescription());
        response.setComplaintStatus(complaint.getComplaintStatus().toString());
        response.setComplaintType(complaint.getComplaintType().toString());
        response.setComplaintDate(complaint.getComplaintDate());

        // Get username
        User user = userRepository.findById(complaint.getUserId()).orElse(null);
        if (user != null) {
            response.setUsername(user.getUsername());
        }

        return response;
    }
}