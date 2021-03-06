package org.nrg.xnat.plugins.ccdb.rest.guest;

import org.nrg.xdat.om.*;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.ResourceFile;
import org.nrg.xnat.helpers.uri.UriParserUtils;
import org.nrg.xnat.plugins.ccdb.service.XnatService;
import org.nrg.xnat.plugins.ccdb.service.XnatServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FrontDesk_WU implements FrontDesk {
    private final GuestPositionLabelModel_WU guestPositionLabelModel;
    private final GuestLabelModel_WU guestLabelModel;
    private final GuestSessionLabelModel_WU guestSessionLabelModel;
    private XnatService _xnatService;

    private static final Logger _log = LoggerFactory.getLogger( "ccdbLogger");

    @Autowired
    public FrontDesk_WU( XnatService xnatService) {
        this( new GuestPositionLabelModel_WU(), new GuestLabelModel_WU(), new GuestSessionLabelModel_WU());
        _xnatService = xnatService;
    }

    public FrontDesk_WU(GuestPositionLabelModel_WU gplm, GuestLabelModel_WU glm, GuestSessionLabelModel_WU gslm) {
        this.guestPositionLabelModel = gplm;
        this.guestLabelModel = glm;
        this.guestSessionLabelModel = gslm;
    }

    public void checkInHotelSession( String projectLabel, String hotelSessionID, UserI user) throws Exception {
        try {
            XnatProjectdata projectdata = XnatProjectdata.getProjectByIDorAlias(projectLabel, user, false);
            if (projectdata != null) {
                List<Guest> guests = getGuests(hotelSessionID, user);

                for (Guest guest : guests) {
                    checkInGuest(guest, projectdata, user);
                }
            } else {
                _log.warn("Cannot check hotel session into unknown project: {}", projectLabel);
            }
        }
        catch( XnatServiceException e) {
            String msg = String.format("Failed creating individual sessions in project '%s' from hotel session '%s'.", projectLabel, hotelSessionID);
            _log.error( msg, e);
        }
    }

    @Override
    public List<Guest> getGuests(String hotelSessionLabel, UserI user) {
        List<Guest> guests = new ArrayList<>();

        XnatExperimentdata exptData = XnatExperimentdata.getXnatExperimentdatasById( hotelSessionLabel, user, false);
        if( exptData instanceof XnatSubjectassessordata) {
            XnatSubjectassessordata subjectassessordata = (XnatSubjectassessordata) exptData;
            XnatSubjectdata subjectdata = subjectassessordata.getSubjectData();

            String modality = getModaliity( subjectassessordata);

            String subjectOrderString = (String) subjectdata.getFieldByName("subjectorder");
            if( subjectOrderString == null || subjectOrderString.isEmpty()) {
                String msg = String.format("Unknown subject order in hotel session: %s", hotelSessionLabel);
                _log.error(msg);
                throw new IllegalArgumentException(msg);
            }
            _log.debug( "subjectOrder: {}", subjectOrderString);

            String[] subjectOrderArray = subjectOrderString.split(",");
            int hotelSize = subjectOrderArray.length;

            List<ResourceFile> fileResources = exptData.getFileResources("microPET");
            // don't know why the above returns all file resources and not just the microPET ones.
            _log.debug( "Found {} fileResources for hotel session {}.", fileResources.size(), hotelSessionLabel);

            String hotelSubjectName = subjectdata.getLabel();
            guestLabelModel.setScanName( hotelSubjectName);
            guestLabelModel.setSubjectOrderArray( subjectOrderArray);

            guestSessionLabelModel.setHotelSubjectName( hotelSubjectName);
            guestSessionLabelModel.setModality( modality);
            guestSessionLabelModel.setSubjectOrderArray( subjectOrderArray);

            switch (hotelSize) {
                case 1:
                    Guest guest = getGuest( 1, modality, 0, fileResources);
                    if( guest == null) {
                        String msg = String.format("No guest found for position 0 of 1-room hotel session %s", hotelSessionLabel);
                        _log.warn( msg);
                        break;
                    }
                    guests.add( guest);
                    break;
                case 2:
                    guest = getGuest( 2, modality, 0, fileResources);
                    if( guest == null) {
                        String msg = String.format("No guest found for position 0 of 2-room hotel session %s", hotelSessionLabel);
                        _log.warn( msg);
                        break;
                    }
                    guests.add( guest);
                    guest = getGuest( 2, modality, 1, fileResources);
                    if( guest == null) {
                        String msg = String.format("No guest found for position 1 of 2-room hotel session %s", hotelSessionLabel);
                        _log.warn( msg);
                        break;
                    }
                    guests.add( guest);
                    break;
                case 4:
                    guest = getGuest( 4, modality, 0, fileResources);
                    if( guest == null) {
                        String msg = String.format("No guest found for position 0 of 4-room hotel session %s", hotelSessionLabel);
                        _log.warn( msg);
                        break;
                    }
                    guests.add( guest);
                    guest = getGuest( 4, modality, 1, fileResources);
                    if( guest == null) {
                        String msg = String.format("No guest found for position 1 of 4-room hotel session %s", hotelSessionLabel);
                        _log.warn( msg);
                        break;
                    }
                    guests.add( guest);
                    guest = getGuest( 4, modality, 2, fileResources);
                    if( guest == null) {
                        String msg = String.format("No guest found for position 2 of 4-room hotel session %s", hotelSessionLabel);
                        _log.warn( msg);
                        break;
                    }
                    guests.add( guest);
                    guest = getGuest( 4, modality, 3, fileResources);
                    if( guest == null) {
                        String msg = String.format("No guest found for position 3 of 4-room hotel session %s", hotelSessionLabel);
                        _log.warn( msg);
                        break;
                    }
                    guests.add( guest);
                    break;
                default:
                    String msg = String.format("Unexpected number of subjects '%d' in subjectOrder '%s' for hotel session %s", hotelSize, subjectOrderString, hotelSessionLabel);
                    _log.error(msg);
                    throw new IllegalArgumentException(msg);
            }
            _log.debug("Checking in {} guests in {}-room hotel.", guests.size(), hotelSize);
        }

        return guests;
    }

    protected String getModaliity( XnatSubjectassessordata sad) {
        String modality = "Unknown";
        if( sad instanceof CcdbHotelpet) {
            modality = "PET";
        }
        else if( sad instanceof CcdbHotelct) {
            modality = "CT";
        }
        return modality;
    }

    protected Guest getGuest( int hotelSize, String modality, int position, List<ResourceFile> resourceFiles) {
        Guest guest = null;
        List<ResourceFile> guestResourceFiles = resourceFiles.stream()
                .filter(r -> r.getF().getName().contains( guestPositionLabelModel.getLabel( hotelSize, position)))
                .collect(Collectors.toCollection(ArrayList::new));
        if( ! guestResourceFiles.isEmpty()) {
            String guestName = guestLabelModel.getLabel( hotelSize, position);
            guest = new Guest(guestName);
            String guestSessionName = guestSessionLabelModel.getLabel( hotelSize, position);
            GuestSession guestSession = new GuestSession(guestSessionName, modality, guestResourceFiles);
            guest.addSession(guestSession);
        }
        return guest;
    }

    public void checkInGuest( Guest guest, XnatProjectdata projectdata, UserI user) throws Exception {
        XnatSubjectdata subjectdata = _xnatService.getOrCreateSubject( projectdata, guest.getLabel(), user);
        String sessionLabel = "sessionLabel";
        for( GuestSession gs: guest.getSessions()) {
            XnatImagesessiondata sessiondata = _xnatService.getOrCreateImageSession(subjectdata, gs.getModality(), gs.getLabel(), user);
            XnatImagescandata scan = _xnatService.createImageScan(sessiondata.getId(), sessiondata.getModality(), "1", "microPET", user);

//            String parentUri = UriParserUtils.getArchiveUri( scan);
            String parentUri = String.format("/archive/experiments/%s/scans/%s", scan.getImageSessionId(), scan.getId());
            final boolean preserveDirectories = false;
            final String label = "microPET";
            final String description = "description";
            final String format = "microPET";
            final String content = "content";
            XnatResourcecatalog resourcecatalog = _xnatService.insertResources( parentUri, gs.getFiles(),  preserveDirectories, label, description, format, content, user);
        }
    }

}
