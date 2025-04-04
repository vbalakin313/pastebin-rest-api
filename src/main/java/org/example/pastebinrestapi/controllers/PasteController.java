package org.example.pastebinrestapi.controllers;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.pastebinrestapi.controllers.helpers.ControllerHelper;
import org.example.pastebinrestapi.dto.AckDto;
import org.example.pastebinrestapi.dto.PasteDto.PasteRequestDto;
import org.example.pastebinrestapi.dto.PasteDto.PasteShortResponseDto;
import org.example.pastebinrestapi.entities.PasteEntity;
import org.example.pastebinrestapi.entities.UserEntity;
import org.example.pastebinrestapi.entities.UserPasteEntity;
import org.example.pastebinrestapi.exceptions.BadRequestException;
import org.example.pastebinrestapi.factories.PasteDtoFactory;
import org.example.pastebinrestapi.repositories.PasteRepository;
import org.example.pastebinrestapi.repositories.UserPasteRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@Transactional
@RequiredArgsConstructor
public class PasteController {

    private final PasteRepository pasteRepository;

    private final UserPasteRepository userPasteRepository;

    private final PasteDtoFactory pasteDtoFactory;

    private final ControllerHelper helper;

    public static final String FETCH_PASTE = "/api/pastes";
    public static final String DELETE_PASTE = "/api/pastes/{paste_id}";
    public static final String CREATE_OR_UPDATE_PASTE = "/api/pastes";

    @GetMapping(FETCH_PASTE)
    public List<PasteShortResponseDto> fetchPastes(@RequestParam(value = "title",required = false) Optional<String> prefixTitle){

        prefixTitle = prefixTitle.filter(title -> !title.trim().isEmpty());

        Stream<PasteEntity> pasteStream = prefixTitle
                .map(pasteRepository::streamAllByTitleStartsWithIgnoreCase)
                .orElseGet(pasteRepository::streamAllBy);

        return pasteStream
                .map(pasteDtoFactory::makePastShortResponseDto)
                .collect(Collectors.toList());
    }

    @PutMapping(CREATE_OR_UPDATE_PASTE)
    public PasteRequestDto createOrUpdatePaste(
            @RequestParam(value = "title", required = false) Optional<String> optionalPasteTitle,
            @RequestParam(value = "paste_id", required = false) Optional<Long> optionalPasteId,
            @RequestParam(value = "content", required = false) Optional<String> optionalPasteContent,
            @RequestParam(value = "is_private", required = false) Boolean pasteIsPrivate) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity currentUser = (UserEntity) authentication.getPrincipal();

        optionalPasteTitle = optionalPasteTitle.filter(title -> !title.trim().isEmpty());

        if (!optionalPasteId.isPresent() && !optionalPasteTitle.isPresent()) {
            throw new BadRequestException("Paste title can't be empty");
        }

        final PasteEntity paste = optionalPasteId
                .map(helper::getPasteOrThrowException)
                .orElseGet(() -> PasteEntity.builder().build());

        paste.setPrivate(pasteIsPrivate);
        paste.setUpdatedAt(Instant.now());

        optionalPasteTitle.ifPresent(paste::setTitle);
        optionalPasteContent.ifPresent(paste::setContent);

        final PasteEntity savedEntity = pasteRepository.save(paste);

        if (!optionalPasteId.isPresent()) {
            UserPasteEntity userPaste = UserPasteEntity.builder()
                    .id(new UserPasteEntity.UserPasteId(currentUser.getId(), savedEntity.getId()))
                    .user(currentUser)
                    .paste(savedEntity)
                    .build();
            userPasteRepository.save(userPaste);
        }

        return pasteDtoFactory.makePastRequestDto(savedEntity);
    }


    @DeleteMapping(DELETE_PASTE)
    public AckDto deletePaste(@PathVariable("paste_id") Long pasteId){

        helper.getPasteOrThrowException(pasteId);

        userPasteRepository.deleteByPasteId(pasteId);

        pasteRepository.deleteById(pasteId);

        return AckDto.makeDefaults(true);
    }

}
